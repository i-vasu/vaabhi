package com.vaabhi.engage;

import com.vaabhi.tailoring.ClientService;
import com.vaabhi.tailoring.OrderEvent;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Loyalty points: earned when an order is delivered (once per order), redeemed or adjusted by hand. */
@Service
public class RewardService {

    private final JdbcClient jdbc;
    private final ClientService clients;

    RewardService(JdbcClient jdbc, ClientService clients) {
        this.jdbc = jdbc;
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public Rewards.RewardSettings settings() {
        return jdbc.sql(
                        "SELECT enabled, points_per_100 AS points_per100, paise_per_point FROM reward_settings WHERE id = 1")
                .query(Rewards.RewardSettings.class)
                .single();
    }

    @Transactional
    public Rewards.RewardSettings saveSettings(Rewards.RewardSettings settings) {
        jdbc.sql("""
                        UPDATE reward_settings SET enabled = :enabled, points_per_100 = :points,
                            paise_per_point = :paise WHERE id = 1
                        """)
                .param("enabled", settings.enabled())
                .param("points", settings.pointsPer100())
                .param("paise", settings.paisePerPoint())
                .update();
        return settings();
    }

    /** Points for an amount: whole ₹100s × points per ₹100. */
    static int pointsFor(long amountPaise, int pointsPer100) {
        return (int) (amountPaise / 10_000) * pointsPer100;
    }

    @EventListener
    void on(OrderEvent event) {
        if (event.type() != OrderEvent.Type.DELIVERED) {
            return;
        }
        var settings = settings();
        int points = pointsFor(event.amountPaise(), settings.pointsPer100());
        if (!settings.enabled() || points <= 0) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO reward_entry (client_id, kind, points, order_id, note, created_by)
                        VALUES (:client, 'EARNED', :points, :order, :note, :by)
                        ON CONFLICT (order_id) WHERE kind = 'EARNED' DO NOTHING
                        """)
                .param("client", event.clientId())
                .param("points", points)
                .param("order", event.orderId())
                .param("note", "Order " + event.number())
                .param("by", event.actor())
                .update();
    }

    @Transactional(readOnly = true)
    public Rewards.RewardAccount account(long clientId) {
        clients.summary(clientId);
        var entries = jdbc.sql("""
                        SELECT r.id, r.kind, r.points, r.order_id, o.number AS order_number, r.note, r.created_by,
                               r.created_at
                        FROM reward_entry r LEFT JOIN shop_order o ON o.id = r.order_id
                        WHERE r.client_id = :client ORDER BY r.created_at DESC, r.id DESC
                        """)
                .param("client", clientId)
                .query(Rewards.RewardEntry.class)
                .list();
        int balance = entries.stream().mapToInt(Rewards.RewardEntry::points).sum();
        return new Rewards.RewardAccount(
                clientId, balance, (long) balance * settings().paisePerPoint(), entries);
    }

    /** Redeems points (never more than the balance) or adjusts the balance by hand. */
    @Transactional
    public Rewards.RewardAccount change(long clientId, Rewards.RewardChange change, @Nullable String actor) {
        var account = account(clientId);
        int points;
        if (change.kind() == Rewards.Kind.REDEEMED) {
            if (change.points() <= 0) {
                throw CouponService.bad("Redeem a positive number of points");
            }
            if (change.points() > account.balance()) {
                throw CouponService.bad("Only " + account.balance() + " points to redeem");
            }
            points = -change.points();
        } else if (change.kind() == Rewards.Kind.ADJUSTED) {
            if (change.points() == 0 || account.balance() + change.points() < 0) {
                throw CouponService.bad("That would leave the balance below zero");
            }
            points = change.points();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points are earned only by delivered orders");
        }
        jdbc.sql("""
                        INSERT INTO reward_entry (client_id, kind, points, order_id, note, created_by)
                        VALUES (:client, :kind, :points, :order, :note, :by)
                        """)
                .param("client", clientId)
                .param("kind", change.kind().name())
                .param("points", points)
                .param("order", change.orderId())
                .param(
                        "note",
                        change.note() == null || change.note().isBlank()
                                ? null
                                : change.note().trim())
                .param("by", actor)
                .update();
        return account(clientId);
    }
}

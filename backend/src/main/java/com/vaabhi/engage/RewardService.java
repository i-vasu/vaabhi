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
        if (event.type() == OrderEvent.Type.CANCELLED) {
            returnRedeemed(event);
            return;
        }
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

    /** Gives back, once, the points redeemed on a cancelled order. */
    private void returnRedeemed(OrderEvent event) {
        int redeemed = jdbc.sql(
                        "SELECT COALESCE(-SUM(points), 0) FROM reward_entry WHERE order_id = :order AND kind = 'REDEEMED'")
                .param("order", event.orderId())
                .query(Integer.class)
                .single();
        if (redeemed <= 0) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO reward_entry (client_id, kind, points, order_id, note, created_by)
                        VALUES (:client, 'RETURNED', :points, :order, :note, :by)
                        ON CONFLICT (order_id) WHERE kind = 'RETURNED' DO NOTHING
                        """)
                .param("client", event.clientId())
                .param("points", redeemed)
                .param("order", event.orderId())
                .param("note", "Order " + event.number() + " cancelled")
                .param("by", event.actor())
                .update();
    }

    /**
     * Redeems points on a new order and returns their value in paise; the order takes it off its total. Locks the
     * customer's points so two bookings cannot spend the same balance.
     */
    @Transactional
    public long redeemOnOrder(long clientId, long orderId, String orderNumber, int points, @Nullable String actor) {
        var settings = settings();
        if (!settings.enabled()) {
            throw CouponService.bad("Loyalty points are switched off");
        }
        jdbc.sql("SELECT id FROM client WHERE id = :client FOR UPDATE")
                .param("client", clientId)
                .query(Long.class)
                .optional();
        int balance = balance(clientId);
        if (points > balance) {
            throw CouponService.bad("Only " + balance + " points to redeem");
        }
        jdbc.sql("""
                        INSERT INTO reward_entry (client_id, kind, points, order_id, note, created_by)
                        VALUES (:client, 'REDEEMED', :points, :order, :note, :by)
                        """)
                .param("client", clientId)
                .param("points", -points)
                .param("order", orderId)
                .param("note", "Order " + orderNumber)
                .param("by", actor)
                .update();
        return (long) points * settings.paisePerPoint();
    }

    private int balance(long clientId) {
        return jdbc.sql("SELECT COALESCE(SUM(points), 0) FROM reward_entry WHERE client_id = :client")
                .param("client", clientId)
                .query(Integer.class)
                .single();
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

    /**
     * Corrects the balance by hand. Points are redeemed on the order they pay for (see {@link #redeemOnOrder}), so the
     * discount and the points always match.
     */
    @Transactional
    public Rewards.RewardAccount change(long clientId, Rewards.RewardChange change, @Nullable String actor) {
        var account = account(clientId);
        int points;
        if (change.kind() == Rewards.Kind.REDEEMED) {
            throw CouponService.bad("Redeem points on the order when booking it");
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

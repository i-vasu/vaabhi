package com.vaabhi.engage;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coupons. The new-order form checks a code and fills the discount from it; a coupon's uses are the orders booked
 * with its code.
 */
@Service
public class CouponService {

    private static final String SELECT = """
            SELECT c.id, c.code, c.description, c.kind, c.value, c.min_order_paise, c.valid_from, c.valid_to,
                   c.max_uses, c.active,
                   (SELECT COUNT(*) FROM shop_order o WHERE upper(o.coupon_code) = c.code AND o.status <> 'CANCELLED')
                       AS uses
            FROM coupon c
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    CouponService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Coupon> list() {
        return jdbc.sql(SELECT + " ORDER BY c.active DESC, c.created_at DESC")
                .query(Coupon.class)
                .list();
    }

    @Transactional
    public Coupon save(@Nullable Long id, Coupon.CouponForm form) {
        if (form.kind() == Coupon.Kind.PERCENT && form.value() > 10_000) {
            throw bad("A percentage cannot be more than 100%");
        }
        if (form.validFrom() != null && form.validTo() != null && form.validTo().isBefore(form.validFrom())) {
            throw bad("The coupon ends before it starts");
        }
        var code = form.code().trim().toUpperCase();
        try {
            var sql = id == null ? """
                      INSERT INTO coupon (code, description, kind, value, min_order_paise, valid_from, valid_to,
                          max_uses, active)
                      VALUES (:code, :description, :kind, :value, :min, :from, :to, :max, :active) RETURNING id
                      """ : """
                      UPDATE coupon SET code = :code, description = :description, kind = :kind, value = :value,
                          min_order_paise = :min, valid_from = :from, valid_to = :to, max_uses = :max, active = :active
                      WHERE id = :id RETURNING id
                      """;
            id = jdbc.sql(sql)
                    .param("code", code)
                    .param(
                            "description",
                            form.description() == null || form.description().isBlank()
                                    ? null
                                    : form.description().trim())
                    .param("kind", form.kind().name())
                    .param("value", form.value())
                    .param("min", form.minOrderPaise())
                    .param("from", form.validFrom())
                    .param("to", form.validTo())
                    .param("max", form.maxUses())
                    .param("active", form.active())
                    .param("id", id)
                    .query(Long.class)
                    .optional()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Code " + code + " is already used", e);
        }
        return byId(id);
    }

    private Coupon byId(long id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id")
                .param("id", id)
                .query(Coupon.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
    }

    /** Whether {@code code} applies to an order of {@code amountPaise} today, and what it takes off. */
    @Transactional(readOnly = true)
    public Coupon.CouponCheck check(String code, long amountPaise) {
        var normalized = code.trim().toUpperCase();
        var coupon = jdbc.sql(SELECT + " WHERE c.code = :code")
                .param("code", normalized)
                .query(Coupon.class)
                .optional()
                .orElse(null);
        var today = LocalDate.now(clock);
        String reason = null;
        if (coupon == null) {
            reason = "No coupon with this code";
        } else if (!coupon.active()) {
            reason = "This coupon is switched off";
        } else if (coupon.validFrom() != null && today.isBefore(coupon.validFrom())) {
            reason = "This coupon starts on " + coupon.validFrom();
        } else if (coupon.validTo() != null && today.isAfter(coupon.validTo())) {
            reason = "This coupon ended on " + coupon.validTo();
        } else if (coupon.maxUses() != null && coupon.uses() >= coupon.maxUses()) {
            reason = "This coupon has been used up";
        } else if (amountPaise < coupon.minOrderPaise()) {
            reason = "Needs an order of at least ₹" + coupon.minOrderPaise() / 100;
        }
        if (reason != null) {
            return new Coupon.CouponCheck(normalized, false, reason, null, 0, 0);
        }
        long discount = coupon.kind() == Coupon.Kind.PERCENT
                ? Math.round(amountPaise * coupon.value() / 10_000.0)
                : Math.min(coupon.value(), amountPaise);
        return new Coupon.CouponCheck(normalized, true, null, coupon.kind(), coupon.value(), discount);
    }

    static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

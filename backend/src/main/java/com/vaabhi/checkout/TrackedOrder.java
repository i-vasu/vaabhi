package com.vaabhi.checkout;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a guest sees when tracking an order. It leaves out the address, email, phone and payment ids, because
 * knowing an order number and phone should not reveal them.
 *
 * @param city where it is going, so the customer knows they have the right order
 * @param trackingStatus the courier's latest status, when shipped through Shiprocket
 * @param trackingUrl the courier's public tracking page, when there is one
 */
public record TrackedOrder(
        String number,
        WebOrder.Status status,
        WebOrder.PaymentMethod paymentMethod,
        String city,
        BigDecimal totalRupees,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime paidAt,
        @Nullable OffsetDateTime shippedAt,
        @Nullable OffsetDateTime deliveredAt,
        @Nullable OffsetDateTime cancelledAt,
        @Nullable String courier,
        @Nullable String trackingNumber,
        @Nullable String trackingStatus,
        @Nullable String trackingUrl,
        List<TrackedLine> lines) {

    public record TrackedLine(
            String productSlug,
            String name,
            @Nullable String colourName,
            @Nullable String size,
            int quantity,
            @Nullable String imageKey) {}

    static TrackedOrder of(WebOrder o) {
        return new TrackedOrder(
                o.number(),
                o.status(),
                o.paymentMethod(),
                o.city(),
                o.totalRupees(),
                o.createdAt(),
                o.paidAt(),
                o.shippedAt(),
                o.deliveredAt(),
                o.cancelledAt(),
                o.courier(),
                o.trackingNumber(),
                o.trackingStatus(),
                o.trackingUrl(),
                o.lines().stream()
                        .map(l -> new TrackedLine(
                                l.productSlug(), l.name(), l.colourName(), l.size(), l.quantity(), l.imageKey()))
                        .toList());
    }
}

package com.vaabhi.tailoring;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Something happened to an order. Notifications, invoices (auto-invoice on delivery) and the activity log listen.
 * CHANGED (items, delivery or discount changed after booking) is refused by the money module while the order has a
 * live invoice.
 *
 * @param amountRupees the payment amount for PAYMENT_RECEIVED, otherwise the order total
 */
public record OrderEvent(
        Type type,
        long orderId,
        String number,
        long clientId,
        String clientName,
        BigDecimal amountRupees,
        @Nullable String actor) {

    public enum Type {
        PLACED,
        CHANGED,
        READY,
        DELIVERED,
        CANCELLED,
        PAYMENT_RECEIVED
    }
}

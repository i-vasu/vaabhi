package com.vaabhi.tailoring;

import org.jspecify.annotations.Nullable;

/**
 * Something happened to an order. Notifications, invoices (auto-invoice on delivery) and the activity log listen.
 *
 * @param amountPaise the payment amount for PAYMENT_RECEIVED, otherwise the order total
 */
public record OrderEvent(
        Type type,
        long orderId,
        String number,
        long clientId,
        String clientName,
        long amountPaise,
        @Nullable String actor) {

    public enum Type {
        PLACED,
        READY,
        DELIVERED,
        CANCELLED,
        PAYMENT_RECEIVED
    }
}

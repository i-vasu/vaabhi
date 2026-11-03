package com.vaabhi.tailoring;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Something happened to an alteration. Notifications, money and the activity log listen.
 *
 * @param amountRupees the amount collected for PAYMENT_RECEIVED, otherwise the alteration total
 */
public record AlterationEvent(
        Type type,
        long alterationId,
        String number,
        long clientId,
        String clientName,
        BigDecimal amountRupees,
        @Nullable String actor) {
    public enum Type {
        BOOKED,
        READY,
        DELIVERED,
        CANCELLED,
        PAYMENT_RECEIVED
    }
}

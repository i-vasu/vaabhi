package com.vaabhi.tailoring;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A booked order with its items, money and payments.
 *
 * @param rewardPoints loyalty points redeemed at booking
 * @param rewardPaise what those points took off the total
 * @param balancePaise total minus everything paid (negative after a refund owed)
 * @param cancelSettlement for a cancelled order, what happened to the money paid on it
 */
public record Order(
        long id,
        String number,
        ClientSummary client,
        OrderStatus status,
        LocalDate orderDate,
        LocalDate deliveryDate,
        @Nullable LocalTime dueTime,
        @Nullable LocalDate trialDate,
        @Nullable Long deliveryPackageId,
        long deliveryChargePaise,
        long itemsPaise,
        OrderForm.DiscountKind discountKind,
        long discountValue,
        long discountPaise,
        @Nullable String couponCode,
        int rewardPoints,
        long rewardPaise,
        long totalPaise,
        long paidPaise,
        long balancePaise,
        @Nullable String notes,
        @Nullable String createdBy,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime deliveredAt,
        @Nullable OffsetDateTime cancelledAt,
        @Nullable CancelSettlement cancelSettlement,
        @Nullable String cancelReason,
        List<OrderItem> items,
        List<OrderPayment> payments) {

    /** NONE: nothing had been paid; REFUNDED: it was all given back; KEPT: the shop kept it. */
    public enum CancelSettlement {
        NONE,
        REFUNDED,
        KEPT
    }
}

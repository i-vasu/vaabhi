package com.vaabhi.tailoring;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A booked order with its items, money and payments.
 *
 * @param balancePaise total minus everything paid (negative after a refund owed)
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
        long totalPaise,
        long paidPaise,
        long balancePaise,
        @Nullable String notes,
        @Nullable String createdBy,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime deliveredAt,
        List<OrderItem> items,
        List<OrderPayment> payments) {}

package com.vaabhi.tailoring;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One row of the orders list.
 *
 * @param overdue not yet delivered and the delivery date has passed
 */
public record OrderSummary(
        long id,
        String number,
        long clientId,
        String clientName,
        String clientPhone,
        OrderStatus status,
        LocalDate orderDate,
        LocalDate deliveryDate,
        @Nullable LocalDate trialDate,
        int itemCount,
        String itemsLabel,
        long totalPaise,
        long balancePaise,
        boolean overdue) {}

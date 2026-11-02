package com.vaabhi.payroll;

import java.time.OffsetDateTime;

/**
 * A work stage someone finished on an order item.
 *
 * @param at when the item left the stage
 * @param ratePaise the price list's piece rate for that stage (0 when the item is not from the price list or the
 *     stage has no rate)
 * @param amountPaise rate × quantity
 */
public record ProductionEntry(
        OffsetDateTime at,
        long staffId,
        String staffName,
        long orderId,
        String orderNumber,
        long itemId,
        String description,
        int quantity,
        long stageId,
        String stageName,
        long ratePaise,
        long amountPaise) {}

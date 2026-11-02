package com.vaabhi.tailoring;

import java.time.LocalDate;

/**
 * One row of the alterations list.
 *
 * @param overdue not yet delivered and the ready-by date has passed
 */
public record AlterationSummary(
        long id,
        String number,
        long clientId,
        String clientName,
        String clientPhone,
        OrderStatus status,
        LocalDate readyBy,
        int itemCount,
        String itemsLabel,
        boolean paid,
        long totalPaise,
        long balancePaise,
        boolean overdue) {}

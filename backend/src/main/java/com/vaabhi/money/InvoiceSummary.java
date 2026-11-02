package com.vaabhi.money;

import java.time.LocalDate;

/** One row of the invoice register. */
public record InvoiceSummary(
        long id,
        String number,
        long orderId,
        String orderNumber,
        long clientId,
        String clientName,
        LocalDate issuedOn,
        long taxablePaise,
        long taxPaise,
        long totalPaise,
        long balancePaise,
        boolean cancelled) {}

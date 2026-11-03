package com.vaabhi.money;

import java.math.BigDecimal;
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
        BigDecimal taxableRupees,
        BigDecimal taxRupees,
        BigDecimal totalRupees,
        BigDecimal balanceRupees,
        boolean cancelled) {}

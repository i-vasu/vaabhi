package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One row of the invoice register. {@code clientName} is the buyer's name as invoiced. */
public record InvoiceSummary(
        long id,
        String number,
        Invoice.Source source,
        @Nullable Long orderId,
        String orderNumber,
        @Nullable Long clientId,
        String clientName,
        LocalDate issuedOn,
        BigDecimal taxableRupees,
        BigDecimal taxRupees,
        BigDecimal totalRupees,
        BigDecimal balanceRupees,
        boolean cancelled) {}

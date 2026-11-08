package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Money a customer still owes on one order or alteration.
 *
 * @param date the delivery date (order) or ready-by date (alteration)
 * @param totalRupees the invoice total when invoiced, else the order or alteration total
 * @param daysLate days since {@code date}; 0 when not yet due
 * @param handedOver the garments are already with the customer
 */
public record Due(
        Kind kind,
        long id,
        String number,
        long clientId,
        String clientName,
        String clientPhone,
        LocalDate date,
        BigDecimal totalRupees,
        BigDecimal paidRupees,
        BigDecimal dueRupees,
        int daysLate,
        boolean handedOver,
        @Nullable String invoiceNumber) {
    public enum Kind {
        ORDER,
        ALTERATION
    }
}

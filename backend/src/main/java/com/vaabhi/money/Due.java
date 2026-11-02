package com.vaabhi.money;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Money a customer still owes on one order or alteration.
 *
 * @param date the delivery date (order) or ready-by date (alteration)
 * @param totalPaise the invoice total when invoiced, else the order or alteration total
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
        long totalPaise,
        long paidPaise,
        long duePaise,
        int daysLate,
        boolean handedOver,
        @Nullable String invoiceNumber) {
    public enum Kind {
        ORDER,
        ALTERATION
    }
}

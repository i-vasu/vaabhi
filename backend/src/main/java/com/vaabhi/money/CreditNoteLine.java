package com.vaabhi.money;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * One line of a credit note: the credited share of an invoice line.
 *
 * @param invoiceLineId the invoice line this credits
 * @param quantity how many of the invoice line's units are credited
 * @param amountRupees the line's credit in all (taxable value plus GST)
 */
public record CreditNoteLine(
        long id,
        long invoiceLineId,
        String description,
        @Nullable String hsnCode,
        int quantity,
        BigDecimal unitRupees,
        BigDecimal gstRate,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal igstRupees,
        BigDecimal amountRupees) {}

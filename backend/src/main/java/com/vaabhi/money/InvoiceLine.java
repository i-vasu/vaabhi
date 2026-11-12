package com.vaabhi.money;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * One line of an invoice: an order item, or the delivery charge, with the tax it carried when issued.
 *
 * @param amountRupees quantity times unit, before the line's share of the order discount
 * @param discountRupees the line's share of the order discount
 * @param taxableRupees value before GST, after the discount
 */
public record InvoiceLine(
        long id,
        String description,
        int quantity,
        BigDecimal unitRupees,
        BigDecimal amountRupees,
        BigDecimal discountRupees,
        @Nullable String hsnCode,
        BigDecimal gstRate,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal igstRupees) {}

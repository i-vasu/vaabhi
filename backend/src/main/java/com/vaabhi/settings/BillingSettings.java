package com.vaabhi.settings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * GST and bill behaviour.
 *
 * @param chargeGst bills carry GST lines only when this is on and a GSTIN is set
 * @param defaultGstRate starting rate (percent) for a new category
 * @param pricesIncludeTax product prices already include GST
 * @param showHsnCodes print HSN/SAC codes on invoices
 * @param discountAppliesTo what a discount at booking is taken off
 * @param autoInvoice create the invoice when an order is delivered
 */
public record BillingSettings(
        boolean chargeGst,

        @Nullable
        @Pattern(
                regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "must be a 15-character GSTIN, e.g. 27ABCDE1234F1Z5")
        String gstin,

        @NotNull @DecimalMin("0") @DecimalMax("28") @Digits(integer = 2, fraction = 2)
        BigDecimal defaultGstRate,

        boolean pricesIncludeTax,
        boolean showHsnCodes,
        @NotNull DiscountBase discountAppliesTo,
        boolean autoInvoice) {

    public enum DiscountBase {
        ORDER_TOTAL,
        ITEMS_BEFORE_TAX
    }

    /** GST lines appear only with both the switch and a GSTIN. */
    public boolean gstApplies() {
        return chargeGst && gstin != null && !gstin.isBlank();
    }

    static BillingSettings defaults() {
        return new BillingSettings(false, null, new BigDecimal("5"), true, false, DiscountBase.ORDER_TOTAL, true);
    }
}

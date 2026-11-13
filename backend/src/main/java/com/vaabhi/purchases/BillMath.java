package com.vaabhi.purchases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** The arithmetic of a supplier bill: the GST split on each line, TDS and the financial year. Stays at paise. */
public final class BillMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    /**
     * GST on one line. An intra-state supply splits the tax into CGST and SGST, SGST taking a stray paisa; an
     * inter-state supply puts it all in IGST.
     */
    public record LineTax(BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal total) {}

    private BillMath() {}

    public static LineTax tax(BigDecimal taxable, BigDecimal ratePercent, boolean interState) {
        var tax = taxable.multiply(ratePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        var total = taxable.add(tax).setScale(2, RoundingMode.HALF_UP);
        if (interState) {
            return new LineTax(zero(), zero(), tax, total);
        }
        var cgst = tax.divide(TWO, 2, RoundingMode.FLOOR);
        return new LineTax(cgst, tax.subtract(cgst), zero(), total);
    }

    /** True when both states are known and differ; a blank on either side counts as the same state. */
    public static boolean interState(@Nullable String supplyState, @Nullable String companyState) {
        if (blank(supplyState) || blank(companyState)) {
            return false;
        }
        return !supplyState.trim().equals(companyState.trim());
    }

    public static BigDecimal tds(BigDecimal taxable, BigDecimal ratePercent) {
        return taxable.multiply(ratePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** The 1 April on or before the given date. */
    public static LocalDate fyStart(LocalDate date) {
        var april = LocalDate.of(date.getYear(), 4, 1);
        return date.isBefore(april) ? april.minusYears(1) : april;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}

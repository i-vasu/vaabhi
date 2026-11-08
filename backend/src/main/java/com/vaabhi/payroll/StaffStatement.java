package com.vaabhi.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One person's account with the shop, oldest first: pay earned (finalised payslips before advances), advances
 * given, and pay handed over.
 *
 * @param balanceRupees what the shop owes them (negative: they owe the shop, e.g. an advance not yet earned back)
 */
public record StaffStatement(long staffId, String name, List<Entry> entries, BigDecimal balanceRupees) {

    /** @param amountRupees earned is positive; advances and pay handed over are negative */
    public record Entry(
            LocalDate date, Kind kind, String description, BigDecimal amountRupees, BigDecimal balanceRupees) {}

    public enum Kind {
        EARNED,
        ADVANCE,
        PAID
    }
}

package com.vaabhi.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Work stages finished between two dates, newest first, with totals per person (most earned first). */
public record ProductionLedger(LocalDate from, LocalDate to, List<ProductionEntry> entries, List<StaffPieces> byStaff) {

    /** @param pieces garments (item quantities) finished, stage by stage */
    public record StaffPieces(long staffId, String name, int pieces, BigDecimal amountRupees) {}
}

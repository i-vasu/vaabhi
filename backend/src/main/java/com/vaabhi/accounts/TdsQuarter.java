package com.vaabhi.accounts;

import java.time.LocalDate;
import java.util.List;

/** The deductees, challans and section totals that make up one quarter's 26Q return. */
public record TdsQuarter(
        String fy,
        int quarter,
        LocalDate from,
        LocalDate to,
        List<TdsDeducteeRow> deductees,
        List<TdsQuarterChallan> challans,
        List<TdsSectionTotal> totals) {}

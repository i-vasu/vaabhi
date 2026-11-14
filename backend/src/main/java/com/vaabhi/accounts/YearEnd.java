package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A financial year's closing stock, close checks and the runs that closed or reopened it. */
public record YearEnd(
        String fy,
        LocalDate from,
        LocalDate to,
        boolean closed,
        boolean stockSaved,
        boolean canReopen,
        List<ClosingStockLine> stock,
        BigDecimal finishedGoodsRupees,
        @Nullable String stockNote,
        BigDecimal closingStockRupees,
        BigDecimal stockBalanceRupees,
        BigDecimal adjustmentRupees,
        BigDecimal profitRupees,
        List<YearEndCheck> checks,
        List<YearCloseRunView> runs) {}

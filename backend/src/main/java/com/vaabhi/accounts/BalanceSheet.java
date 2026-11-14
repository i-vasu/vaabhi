package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The Schedule III balance sheet as at a date, with the same date a year earlier beside it. */
public record BalanceSheet(
        LocalDate asOf,
        LocalDate previousAsOf,
        List<StatementLine> equityAndLiabilities,
        List<StatementLine> assets,
        BigDecimal totalEquityAndLiabilitiesRupees,
        BigDecimal totalAssetsRupees,
        BigDecimal previousTotalEquityAndLiabilitiesRupees,
        BigDecimal previousTotalAssetsRupees,
        boolean balanced) {}

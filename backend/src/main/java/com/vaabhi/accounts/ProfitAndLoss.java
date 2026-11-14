package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The Schedule III profit and loss account for a period, with the preceding period beside it. */
public record ProfitAndLoss(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        List<StatementLine> income,
        List<StatementLine> expenses,
        BigDecimal totalIncomeRupees,
        BigDecimal totalExpensesRupees,
        BigDecimal profitRupees,
        BigDecimal previousProfitRupees) {}

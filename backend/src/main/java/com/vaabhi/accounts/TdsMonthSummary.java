package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.YearMonth;

/** What one section owes for one month: deducted on bills less what its challans paid. */
public record TdsMonthSummary(
        String sectionCode,
        String sectionName,
        YearMonth month,
        BigDecimal deductedRupees,
        BigDecimal paidRupees,
        BigDecimal balanceRupees) {}

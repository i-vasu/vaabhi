package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One TDS challan paid during a quarter, for the 26Q return. */
public record TdsQuarterChallan(
        long id,
        String sectionCode,
        LocalDate periodMonth,
        BigDecimal amountRupees,
        BigDecimal interestRupees,
        String challanNo,
        String bsrCode,
        LocalDate paidOn) {}

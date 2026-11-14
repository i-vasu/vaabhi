package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** One TDS challan and whether the sweeper has posted it. */
public record TdsChallanView(
        long id,
        long tdsSectionId,
        String sectionCode,
        YearMonth periodMonth,
        BigDecimal amountRupees,
        BigDecimal interestRupees,
        String challanNo,
        String bsrCode,
        LocalDate paidOn,
        String mode,
        boolean posted) {}

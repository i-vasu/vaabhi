package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One bill in a quarter that TDS was deducted on, for the 26Q return. */
public record TdsDeducteeRow(
        long billId,
        String sectionCode,
        String partyName,
        @Nullable String pan,
        String billNo,
        LocalDate creditedOn,
        BigDecimal amountRupees,
        BigDecimal ratePercent,
        BigDecimal tdsRupees,
        @Nullable String challanNo) {}

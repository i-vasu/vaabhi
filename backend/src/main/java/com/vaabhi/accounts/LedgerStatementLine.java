package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One posting on an account, with the account's running balance after it. */
public record LedgerStatementLine(
        long entryId,
        String number,
        LocalDate date,
        String narration,
        @Nullable String partyName,
        @Nullable String memo,
        BigDecimal debitRupees,
        BigDecimal creditRupees,
        BigDecimal balanceRupees) {}

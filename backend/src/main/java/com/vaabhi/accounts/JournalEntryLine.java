package com.vaabhi.accounts;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** A posted line with its account and party names. */
public record JournalEntryLine(
        long accountId,
        String accountCode,
        String accountName,
        @Nullable Long partyId,
        @Nullable String partyName,
        BigDecimal debitRupees,
        BigDecimal creditRupees,
        @Nullable String memo) {}

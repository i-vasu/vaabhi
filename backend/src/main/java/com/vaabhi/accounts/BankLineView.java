package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One movement on a statement, with the journal entry it is matched or posted to. */
public record BankLineView(
        long id,
        LocalDate date,
        String description,
        @Nullable String reference,
        BigDecimal debitRupees,
        BigDecimal creditRupees,
        @Nullable BigDecimal balanceRupees,
        String status,
        @Nullable Long journalEntryId,
        @Nullable String journalNumber,
        @Nullable String ignoreReason) {}

package com.vaabhi.accounts;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** One line of a manual journal entry: a debit or a credit (never both) on an account. */
public record ManualEntryLineInput(
        long accountId,
        @Nullable Long partyId,
        @NotNull BigDecimal debitRupees,
        @NotNull BigDecimal creditRupees,
        @Nullable @Size(max = 120) String memo) {}

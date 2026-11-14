package com.vaabhi.accounts;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** One line of an entry to post: a debit or a credit (never both) on an account, optionally for a party. */
public record NewEntryLine(
        long accountId,
        @Nullable Long partyId,
        BigDecimal debitRupees,
        BigDecimal creditRupees,
        @Nullable String memo) {}

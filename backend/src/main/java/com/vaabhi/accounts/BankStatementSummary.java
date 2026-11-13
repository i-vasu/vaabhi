package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** One uploaded bank statement in the list. */
public record BankStatementSummary(
        long id,
        String fileName,
        LocalDate periodFrom,
        LocalDate periodTo,
        @Nullable BigDecimal openingRupees,
        @Nullable BigDecimal closingRupees,
        int lineCount,
        int unmatchedCount,
        OffsetDateTime uploadedAt) {}

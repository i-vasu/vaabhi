package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A Bank journal line in the statement period that no bank line links to. */
public record LedgerOnlyMovement(
        long journalEntryId,
        String number,
        LocalDate date,
        String narration,
        BigDecimal debitRupees,
        BigDecimal creditRupees) {}

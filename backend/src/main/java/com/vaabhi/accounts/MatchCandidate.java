package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A journal entry a bank line could be matched to, with the absolute size of its Bank movement. */
public record MatchCandidate(
        long journalEntryId, String number, LocalDate date, String narration, BigDecimal amountRupees) {}

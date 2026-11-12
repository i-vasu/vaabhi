package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row in the journal list. */
public record JournalListItem(
        long id,
        String number,
        LocalDate date,
        String narration,
        String sourceType,
        String sourceStep,
        BigDecimal totalRupees,
        boolean reversed) {}

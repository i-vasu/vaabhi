package com.vaabhi.accounts;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A posted journal entry. {@code reversedById} is set once a reversal exists. */
public record JournalEntry(
        long id,
        String number,
        LocalDate date,
        String sourceType,
        @Nullable Long sourceId,
        String sourceStep,
        String narration,
        @Nullable String orderKey,
        @Nullable Long reversesId,
        @Nullable Long reversedById,
        @Nullable String createdBy,
        OffsetDateTime createdAt,
        @Nullable JournalSourceLink sourceLink,
        List<JournalEntryLine> lines) {}

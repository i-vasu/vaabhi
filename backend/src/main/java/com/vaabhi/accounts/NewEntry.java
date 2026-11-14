package com.vaabhi.accounts;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** An entry to post. {@code sourceId} is null for manual and opening entries; {@code reversesId} only on reversals. */
public record NewEntry(
        LocalDate date,
        String sourceType,
        @Nullable Long sourceId,
        String sourceStep,
        String narration,
        @Nullable String orderKey,
        @Nullable Long reversesId,
        List<NewEntryLine> lines) {}

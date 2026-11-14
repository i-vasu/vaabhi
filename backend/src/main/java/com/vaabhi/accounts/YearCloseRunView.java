package com.vaabhi.accounts;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** One close of a financial year, and the entries it posted. */
public record YearCloseRunView(
        long id,
        @Nullable Long stockEntryId,
        @Nullable Long closingEntryId,
        @Nullable String closedBy,
        OffsetDateTime closedAt,
        @Nullable String reopenedBy,
        @Nullable OffsetDateTime reopenedAt) {}

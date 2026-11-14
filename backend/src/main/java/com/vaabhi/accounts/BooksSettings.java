package com.vaabhi.accounts;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The books start date, which account each payment mode lands in, and the party Shiprocket is a supplier under. */
public record BooksSettings(
        @Nullable LocalDate startDate,
        Map<String, Long> modeAccounts,
        @Nullable OffsetDateTime lastSweepAt,
        @Nullable Long shiprocketPartyId) {

    /** Settings for callers that do not set the Shiprocket supplier. */
    public BooksSettings(
            @Nullable LocalDate startDate, Map<String, Long> modeAccounts, @Nullable OffsetDateTime lastSweepAt) {
        this(startDate, modeAccounts, lastSweepAt, null);
    }
}

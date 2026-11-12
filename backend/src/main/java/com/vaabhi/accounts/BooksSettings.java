package com.vaabhi.accounts;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The books start date and which account each payment mode lands in. */
public record BooksSettings(
        @Nullable LocalDate startDate,
        Map<String, Long> modeAccounts,
        @Nullable OffsetDateTime lastSweepAt) {}

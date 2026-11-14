package com.vaabhi.accounts;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** A month of the books ({@code month} is its first day) and whether it is locked. */
public record PeriodStatus(
        LocalDate month,
        boolean locked,
        @Nullable String lockedBy,
        @Nullable OffsetDateTime lockedAt) {}

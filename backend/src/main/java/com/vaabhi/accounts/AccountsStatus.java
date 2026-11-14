package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** Whether the books are running: start date, last sweep, unposted problems and any trial balance difference. */
public record AccountsStatus(
        @Nullable LocalDate startDate,
        @Nullable OffsetDateTime lastSweepAt,
        int problemCount,
        BigDecimal trialBalanceDifference) {}

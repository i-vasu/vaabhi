package com.vaabhi.staff;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Raise a request. Leave, off days and comp-offs cover {@code fromDate} to {@code toDate}; a permission and an
 * advance are for {@code fromDate} only.
 *
 * @param staffId who it is for (managers raising one for someone else); null for yourself
 */
public record RequestForm(
        @Nullable Long staffId,
        @NotNull StaffRequest.Kind kind,
        @NotNull LocalDate fromDate,
        @Nullable LocalDate toDate,
        @Nullable @DecimalMin("0.5") @DecimalMax("8") BigDecimal hours,
        @Nullable @Min(100) @Max(100_000_000) Long amountPaise,
        @Nullable @Size(max = 500) String reason) {}

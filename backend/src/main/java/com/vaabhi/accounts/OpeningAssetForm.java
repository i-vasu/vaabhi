package com.vaabhi.accounts;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** An asset the shop already owned when the books started. */
public record OpeningAssetForm(
        @NotBlank @Size(max = 120) String name,
        @NotNull Long classId,
        @NotNull @DecimalMin("0.01") BigDecimal costRupees,
        @NotNull LocalDate putToUseOn,
        @NotNull @DecimalMin("0") BigDecimal openingAccdepRupees) {}

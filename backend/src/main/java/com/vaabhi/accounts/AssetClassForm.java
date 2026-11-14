package com.vaabhi.accounts;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Create or edit an asset class. */
public record AssetClassForm(
        @NotBlank @Size(max = 60) String name,
        @Min(1) @Max(60) int lifeYears,
        @NotNull @DecimalMin("0") @DecimalMax("50") BigDecimal residualPercent,
        boolean active) {}

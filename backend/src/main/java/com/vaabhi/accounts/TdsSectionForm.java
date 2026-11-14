package com.vaabhi.accounts;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Create or edit a TDS section. */
public record TdsSectionForm(
        @NotBlank @Size(max = 10) String code,
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0") @DecimalMax("30") BigDecimal ratePercent,
        @NotNull @DecimalMin("0") @DecimalMax("30") BigDecimal noPanRatePercent,
        @Nullable BigDecimal yearThresholdRupees,
        @Nullable BigDecimal singleThresholdRupees,
        boolean active) {}

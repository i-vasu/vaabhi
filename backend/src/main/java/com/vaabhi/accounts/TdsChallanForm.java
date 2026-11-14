package com.vaabhi.accounts;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Pay TDS for a month. {@code periodMonth} is the month the TDS belongs to, as yyyy-MM. */
public record TdsChallanForm(
        @NotNull Long tdsSectionId,
        @NotBlank String periodMonth,
        @NotNull @DecimalMin("0.01") BigDecimal amountRupees,
        @NotNull @DecimalMin("0") BigDecimal interestRupees,
        @NotBlank @Size(max = 20) String challanNo,

        @NotBlank @Pattern(regexp = "[0-9]{7}", message = "Give the seven-digit BSR code")
        String bsrCode,

        @NotNull LocalDate paidOn,
        @NotNull String mode) {}

package com.vaabhi.money;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Add or edit a tax class. The price limit and the rate above it come together or not at all. */
public record TaxClassForm(
        @NotBlank @Size(max = 60) String name,
        @NotNull TaxClass.Kind kind,

        @NotNull @Pattern(regexp = "^[0-9]{4,8}$", message = "must be 4 to 8 digits")
        String hsnCode,

        @NotNull @DecimalMin("0") @DecimalMax("28") @Digits(integer = 2, fraction = 2)
        BigDecimal ratePercent,

        @Nullable @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal slabLimitRupees,

        @Nullable @DecimalMin("0") @DecimalMax("28") @Digits(integer = 2, fraction = 2)
        BigDecimal rateAbovePercent) {}

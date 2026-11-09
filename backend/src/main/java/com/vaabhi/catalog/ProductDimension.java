package com.vaabhi.catalog;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * One measurement of a product, e.g. kurti length 44 in or dupatta width 36 in. Which ones a product has depends
 * on its category (the admin offers the fields); each keeps its label and unit so the store can show it as is.
 *
 * @param code stable key, e.g. {@code length}
 */
public record ProductDimension(
        @NotBlank @Pattern(regexp = "^[a-z0-9]++(?:-[a-z0-9]++)*+$") @Size(max = 40)
        String code,

        @NotBlank @Size(max = 60) String label,
        @NotNull Unit unit,

        @NotNull @DecimalMin("0.01") @DecimalMax("99999") @Digits(integer = 5, fraction = 2)
        BigDecimal value) {

    public enum Unit {
        IN,
        CM,
        M
    }
}

package com.vaabhi.catalog;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** An optional extra (e.g. blouse stitching) as the admin edits it. {@code priceRupees} 0 means included. */
public record AddonForm(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]++(?:-[a-z0-9]++)*+$", message = "use lowercase letters, digits and dashes")
        @Size(max = 40)
        String code,

        @NotNull AddonKind kind,
        @NotBlank @Size(max = 80) String name,
        @Nullable @Size(max = 300) String description,
        @PositiveOrZero @Digits(integer = 7, fraction = 2) BigDecimal priceRupees,
        @NotNull AddonInput input,
        boolean active) {}

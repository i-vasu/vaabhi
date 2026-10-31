package com.vaabhi.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A colour × size variant as the admin edits it. {@code sku} is generated when blank; {@code size} null means
 * one size. Prices are in paise.
 */
public record VariantForm(
        @Nullable @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{0,63}$", message = "use capital letters, digits and dashes")
        String sku,

        @NotBlank @Size(max = 60) String colourName,

        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a colour like #6E2A35")
        String colourHex,

        @NotBlank @Size(max = 30) String colourFamily,
        @Nullable @Size(max = 10) String size,
        @Positive int pricePaise,
        @Positive int mrpPaise,
        @PositiveOrZero int stockQty,
        boolean active) {}

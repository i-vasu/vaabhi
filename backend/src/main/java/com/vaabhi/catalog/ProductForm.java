package com.vaabhi.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Create or replace a product (everything except its status and photos). Variants and add-ons are the complete
 * list: rows left out are removed.
 *
 * @param slug URL name; generated from the name when blank
 * @param attributes facet value codes by facet code, e.g. {@code fabric -> [silk]}
 */
public record ProductForm(
        @NotBlank @Size(max = 160) String name,

        @Nullable
        @Pattern(regexp = "^[a-z0-9]++(?:-[a-z0-9]++)*+$", message = "use lowercase letters, digits and dashes")
        @Size(max = 160)
        String slug,

        @Nullable @Size(max = 200) String subtitle,
        @Nullable @Size(max = 5000) String description,
        @NotBlank String categorySlug,
        @Nullable Badge badge,
        @Nullable @Size(max = 2000) String careInstructions,
        @Nullable @DecimalMin("0.5") @DecimalMax("9.9") BigDecimal sareeLengthM,
        boolean blouseIncluded,
        @Nullable @DecimalMin("0.1") @DecimalMax("2.0") BigDecimal blouseLengthM,
        @NotNull Map<String, List<String>> attributes,
        @NotNull @Size(max = 200) List<@Valid @NotNull VariantForm> variants,
        @NotNull @Size(max = 10) List<@Valid @NotNull AddonForm> addons) {}

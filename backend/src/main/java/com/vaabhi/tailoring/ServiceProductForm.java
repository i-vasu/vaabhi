package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Add or edit a price-list product.
 *
 * @param code leave blank to take the next item code
 */
public record ServiceProductForm(
        @NotNull Long templateId,

        @Nullable @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9/_\\-]*$", message = "letters, digits, / - _ only")
        String code,

        @NotBlank @Size(max = 80) String name,
        @Min(0) @Max(100_000_000) long pricePaise,
        @Nullable @Size(max = 200) String photoKey,
        @NotNull @Size(max = 20) List<@Valid StageCost> stageCosts) {}

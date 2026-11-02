package com.vaabhi.tailoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * One garment brought for alteration.
 *
 * @param garment what it is, e.g. "Blue silk blouse"
 * @param costPaise charged for this piece (ignored on a free alteration)
 */
public record AlterationItemForm(
        @NotNull AlterationKind kind,
        @NotBlank @Size(max = 200) String garment,
        @Nullable @Size(max = 500) String instructions,
        @Nullable @Size(max = 200) String photoKey,
        @Min(0) @Max(100_000_000) long costPaise) {}

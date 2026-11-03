package com.vaabhi.tailoring;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * One garment brought for alteration.
 *
 * @param garment what it is, e.g. "Blue silk blouse"
 * @param costRupees charged for this piece (ignored on a free alteration)
 */
public record AlterationItemForm(
        @NotNull AlterationKind kind,
        @NotBlank @Size(max = 200) String garment,
        @Nullable @Size(max = 500) String instructions,
        @Nullable @Size(max = 200) String photoKey,

        @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal costRupees) {}

package com.vaabhi.tailoring;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** One garment of an alteration. */
public record AlterationItem(
        long id,
        AlterationKind kind,
        String garment,
        @Nullable String instructions,
        @Nullable String photoKey,
        BigDecimal costRupees) {}

package com.vaabhi.tailoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Record money received for an order (or a refund given back). */
public record PaymentForm(
        @Min(1) @Max(100_000_000) long amountPaise,
        @NotNull PaymentMode mode,
        boolean refund,
        @Nullable @Size(max = 200) String note) {}

package com.vaabhi.tailoring;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Record money received for an order (or a refund given back). */
public record PaymentForm(
        @DecimalMin("0.01") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal amountRupees,

        @NotNull PaymentMode mode,
        boolean refund,
        @Nullable @Size(max = 200) String note) {}

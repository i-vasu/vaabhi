package com.vaabhi.checkout;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The returned items came back: how much to refund (up to what the customer paid and has not had back yet) and,
 * for a cash-on-delivery order, the UPI or bank reference of the money the shop sent.
 */
public record ReturnRefundForm(
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2)
        BigDecimal amountRupees,

        @Nullable @Size(max = 80) String reference) {}

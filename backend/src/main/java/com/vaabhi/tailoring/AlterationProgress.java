package com.vaabhi.tailoring;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Move an alteration on. Alterations have no production stages: BOOKED → IN_PROGRESS → READY → DELIVERED, or
 * CANCELLED.
 *
 * @param collectRupees money taken now (usually on delivery); added to what was collected
 * @param mode how it was paid, or how a refund is given back; required whenever money moves
 * @param settlement when cancelling after money was collected: give it back (REFUND) or keep it (KEEP)
 */
public record AlterationProgress(
        @NotNull OrderStatus status,

        @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal collectRupees,

        @Nullable PaymentMode mode,

        CancelForm.@Nullable Settlement settlement) {}

package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Change what was booked on an open order: its items, delivery package and discount. Dates and notes are changed with
 * the schedule. Points redeemed at booking stay as they are.
 *
 * @param items every item the order should have; existing ones carry their id, new ones none. An item left out is
 *     removed, which is only possible before it has left its first stage.
 * @param discountValue rupees for AMOUNT, percent for PERCENT (10.5 = 10.5 %)
 */
public record OrderChange(
        @Nullable Long deliveryPackageId,
        @NotNull @Size(min = 1, max = 30) List<@Valid OrderItemChange> items,
        @NotNull OrderForm.DiscountKind discountKind,

        @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal discountValue,

        @Nullable @Size(max = 40) String couponCode) {}

package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Change what was booked on an open order: its items, delivery package and discount. Dates and notes are changed with
 * the schedule. Points redeemed at booking stay as they are.
 *
 * @param items every item the order should have; existing ones carry their id, new ones none. An item left out is
 *     removed, which is only possible before it has left its first stage.
 * @param discountValue paise for AMOUNT, basis points for PERCENT (10 % = 1000)
 */
public record OrderChange(
        @Nullable Long deliveryPackageId,
        @NotNull @Size(min = 1, max = 30) List<@Valid OrderItemChange> items,
        @NotNull OrderForm.DiscountKind discountKind,
        @Min(0) @Max(100_000_000) long discountValue,
        @Nullable @Size(max = 40) String couponCode) {}

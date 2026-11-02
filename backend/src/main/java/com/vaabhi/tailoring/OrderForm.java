package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Book a new order.
 *
 * @param deliveryPackageId a delivery package; its charge is added and, without a delivery date, its days decide it
 * @param discountValue paise for AMOUNT, basis points for PERCENT (10 % = 1000)
 * @param advancePaise paid at booking (recorded as an ADVANCE payment)
 */
public record OrderForm(
        @NotNull Long clientId,
        @NotNull LocalDate orderDate,
        @Nullable LocalDate deliveryDate,
        @Nullable LocalTime dueTime,
        @Nullable LocalDate trialDate,
        @Nullable Long deliveryPackageId,
        @NotNull @Size(min = 1, max = 30) List<@Valid OrderItemForm> items,
        @NotNull DiscountKind discountKind,
        @Min(0) @Max(100_000_000) long discountValue,
        @Nullable @Size(max = 40) String couponCode,
        @Min(0) @Max(100_000_000) long advancePaise,
        @NotNull PaymentMode advanceMode,
        @Nullable @Size(max = 2000) String notes) {

    public enum DiscountKind {
        AMOUNT,
        PERCENT
    }
}

package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Book a new order.
 *
 * @param deliveryPackageId a delivery package; its charge is added and, without a delivery date, its days decide it
 * @param discountValue rupees for AMOUNT, percent for PERCENT (10.5 = 10.5 %)
 * @param couponCode checked again on booking: it must still be valid and not used up
 * @param redeemPoints loyalty points to redeem on this order; their value comes off the total
 * @param advanceRupees paid at booking (recorded as an ADVANCE payment)
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

        @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal discountValue,

        @Nullable @Size(max = 40) String couponCode,
        @Min(0) @Max(1_000_000) int redeemPoints,

        @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal advanceRupees,

        @NotNull @CounterMode PaymentMode advanceMode,
        @Nullable @Size(max = 2000) String notes) {

    public enum DiscountKind {
        AMOUNT,
        PERCENT
    }
}

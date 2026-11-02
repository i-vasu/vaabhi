package com.vaabhi.engage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A discount code.
 *
 * @param value paise for AMOUNT, basis points (10 % = 1000) for PERCENT
 * @param uses orders (not cancelled) booked with this code
 */
public record Coupon(
        long id,
        String code,
        @Nullable String description,
        Kind kind,
        long value,
        long minOrderPaise,
        @Nullable LocalDate validFrom,
        @Nullable LocalDate validTo,
        @Nullable Integer maxUses,
        boolean active,
        int uses) {

    public enum Kind {
        AMOUNT,
        PERCENT
    }

    /** Add or edit a coupon; the code is stored in capitals. */
    public record CouponForm(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "letters, digits, - and _ only")
            String code,

            @Nullable @Size(max = 200) String description,
            @NotNull Kind kind,
            @Min(1) @Max(100_000_000) long value,
            @Min(0) @Max(100_000_000) long minOrderPaise,
            @Nullable LocalDate validFrom,
            @Nullable LocalDate validTo,
            @Nullable @Min(1) Integer maxUses,
            boolean active) {}

    /**
     * Whether a code can be used on an order now.
     *
     * @param reason why not, when it cannot
     * @param discountPaise what it takes off {@code amountPaise}
     */
    public record CouponCheck(
            String code,
            boolean valid,
            @Nullable String reason,
            @Nullable Kind kind,
            long value,
            long discountPaise) {}
}

package com.vaabhi.engage;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A discount code.
 *
 * @param value rupees for AMOUNT, percent for PERCENT (10.5 = 10.5 %)
 * @param uses orders (not cancelled) booked with this code
 */
public record Coupon(
        long id,
        String code,
        @Nullable String description,
        Kind kind,
        BigDecimal value,
        BigDecimal minOrderRupees,
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

            @DecimalMin("0.01") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
            BigDecimal value,

            @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
            BigDecimal minOrderRupees,

            @Nullable LocalDate validFrom,
            @Nullable LocalDate validTo,
            @Nullable @Min(1) Integer maxUses,
            boolean active) {}

    /**
     * Whether a code can be used on an order now.
     *
     * @param reason why not, when it cannot
     * @param discountRupees what it takes off {@code amountRupees}
     */
    public record CouponCheck(
            String code,
            boolean valid,
            @Nullable String reason,
            @Nullable Kind kind,
            @Nullable BigDecimal value,
            BigDecimal discountRupees) {}
}

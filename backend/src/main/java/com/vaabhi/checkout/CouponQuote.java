package com.vaabhi.checkout;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * What a coupon takes off a bag now.
 *
 * @param code the code as stored (upper case)
 * @param reason why not, when {@code valid} is false
 * @param discountRupees what the code takes off {@code itemsRupees} (zero when not valid)
 * @param itemsRupees the price of the lines
 */
public record CouponQuote(
        String code, boolean valid, @Nullable String reason, BigDecimal discountRupees, BigDecimal itemsRupees) {}

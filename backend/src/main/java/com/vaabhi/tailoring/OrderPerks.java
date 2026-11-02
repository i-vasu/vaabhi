package com.vaabhi.tailoring;

import org.jspecify.annotations.Nullable;

/**
 * Coupons and loyalty points for bookings, implemented by the engage module (which depends on this one), so booking
 * and editing an order can check them in the same transaction.
 */
public interface OrderPerks {

    /**
     * Fails with 400 and the reason when {@code code} cannot be used on an order of {@code amountPaise} today. Locks
     * the coupon until the transaction ends, so two bookings cannot both take its last use.
     */
    void requireCoupon(String code, long amountPaise);

    /**
     * Redeems {@code points} of the customer's balance on an order and returns what they are worth in paise. Fails
     * with 400 when rewards are off or the balance is too small.
     */
    long redeem(long clientId, long orderId, String orderNumber, int points, @Nullable String actor);
}

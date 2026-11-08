package com.vaabhi.engage;

import com.vaabhi.tailoring.OrderPerks;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Lets booking and editing an order check coupons and redeem points (see {@link OrderPerks}). */
@Component
class EngageOrderPerks implements OrderPerks {

    private final CouponService coupons;
    private final RewardService rewards;

    EngageOrderPerks(CouponService coupons, RewardService rewards) {
        this.coupons = coupons;
        this.rewards = rewards;
    }

    @Override
    public void requireCoupon(String code, BigDecimal amountRupees) {
        coupons.requireForBooking(code, amountRupees);
    }

    @Override
    public BigDecimal redeem(long clientId, long orderId, String orderNumber, int points, @Nullable String actor) {
        return rewards.redeemOnOrder(clientId, orderId, orderNumber, points, actor);
    }
}

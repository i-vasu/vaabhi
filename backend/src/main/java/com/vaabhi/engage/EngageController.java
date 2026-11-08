package com.vaabhi.engage;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coupons, rewards and customer messages. Coupon checks, a customer's points and messages need ORDERS; managing
 * coupons and reward settings needs MARKETING (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/engage")
class EngageController {

    private final CouponService coupons;
    private final RewardService rewards;
    private final MessageService messages;

    EngageController(CouponService coupons, RewardService rewards, MessageService messages) {
        this.coupons = coupons;
        this.rewards = rewards;
        this.messages = messages;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    @GetMapping("/coupons")
    List<Coupon> listCoupons() {
        return coupons.list();
    }

    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    Coupon createCoupon(@Valid @RequestBody Coupon.CouponForm form) {
        return coupons.save(null, form);
    }

    @PutMapping("/coupons/{id}")
    Coupon updateCoupon(@PathVariable long id, @Valid @RequestBody Coupon.CouponForm form) {
        return coupons.save(id, form);
    }

    /** Whether a code applies to an order of {@code amountRupees} today. */
    @GetMapping("/coupons/check")
    Coupon.CouponCheck checkCoupon(@RequestParam String code, @RequestParam BigDecimal amountRupees) {
        return coupons.check(code, amountRupees);
    }

    @GetMapping("/rewards/settings")
    Rewards.RewardSettings getRewardSettings() {
        return rewards.settings();
    }

    @PutMapping("/rewards/settings")
    Rewards.RewardSettings saveRewardSettings(@Valid @RequestBody Rewards.RewardSettings settings) {
        return rewards.saveSettings(settings);
    }

    @GetMapping("/rewards/clients/{clientId}")
    Rewards.RewardAccount getRewardAccount(@PathVariable long clientId) {
        return rewards.account(clientId);
    }

    @PostMapping("/rewards/clients/{clientId}")
    Rewards.RewardAccount changeRewards(
            @PathVariable long clientId,
            @Valid @RequestBody Rewards.RewardChange change,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return rewards.change(clientId, change, actor(jwt));
    }

    @GetMapping("/messages")
    List<Message> listMessages(
            @RequestParam(required = false) @Nullable Long clientId,
            @RequestParam(required = false) @Nullable Long orderId) {
        return messages.list(clientId, orderId, null);
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    Message prepareMessage(@Valid @RequestBody Message.MessageForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return messages.prepare(form, actor(jwt));
    }
}

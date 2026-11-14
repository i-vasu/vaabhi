package com.vaabhi.checkout;

import org.jspecify.annotations.Nullable;

/** One settlement batch as Razorpay reports it: amounts in paise and {@code createdAt} in epoch seconds. */
public record RazorpaySettlementData(
        String id,
        @Nullable String utr,
        long amountPaise,
        long feesPaise,
        long taxPaise,
        String status,
        long createdAt) {}

package com.vaabhi.checkout;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * What the browser needs to open Razorpay Checkout for an order just placed.
 *
 * @param amountPaise the total in paise, as Razorpay Checkout expects it (the order itself is in rupees)
 */
public record CheckoutStart(
        String orderNumber,
        BigDecimal totalRupees,
        String razorpayOrderId,
        String razorpayKeyId,
        long amountPaise,
        String currency,
        String name,
        String phone,
        @Nullable String email) {}

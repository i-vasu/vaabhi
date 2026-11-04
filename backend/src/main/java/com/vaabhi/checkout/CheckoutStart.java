package com.vaabhi.checkout;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The order just placed. For ONLINE orders it carries what the browser needs to open Razorpay Checkout; COD orders
 * are already PLACED and need nothing more.
 *
 * @param amountPaise the total in paise, as Razorpay Checkout expects it (the order itself is in rupees)
 */
public record CheckoutStart(
        String orderNumber,
        WebOrder.Status status,
        WebOrder.PaymentMethod paymentMethod,
        BigDecimal totalRupees,
        @Nullable String razorpayOrderId,
        @Nullable String razorpayKeyId,
        long amountPaise,
        String currency,
        String name,
        String phone,
        @Nullable String email) {}

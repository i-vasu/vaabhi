package com.vaabhi.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What Razorpay Checkout's success handler returns to the browser. */
public record PaymentConfirmation(
        @NotBlank @Size(max = 40) String razorpayOrderId,
        @NotBlank @Size(max = 40) String razorpayPaymentId,
        @NotBlank @Size(max = 128) String razorpaySignature) {}

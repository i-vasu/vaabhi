package com.vaabhi.checkout;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Razorpay API keys (test or live). Without them checkout answers 503. */
@ConfigurationProperties("vaabhi.razorpay")
record RazorpayProperties(
        @Nullable String keyId,
        @Nullable String keySecret,
        @Nullable String webhookSecret,
        @Nullable String baseUrl) {

    RazorpayProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.razorpay.com" : baseUrl;
    }

    boolean configured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}

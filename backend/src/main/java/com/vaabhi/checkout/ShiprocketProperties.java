package com.vaabhi.checkout;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shiprocket API user (Shiprocket → Settings → API → Configure, a separate user from the dashboard login), the
 * pickup address name as set up in Shiprocket, and the parcel size used for every order (products have no weight
 * yet). Without the API user, "Ship with Shiprocket" answers 503 and checkout skips the pincode check.
 *
 * @param pickupLocation the pickup address's nickname in Shiprocket (Settings → Pickup Addresses), e.g. "Primary"
 * @param pickupPincode the pickup address's pincode, for the serviceability check at checkout
 * @param webhookToken the token set on Shiprocket's webhook (sent back in the x-api-key header)
 */
@ConfigurationProperties("vaabhi.shiprocket")
record ShiprocketProperties(
        @Nullable String email,
        @Nullable String password,
        @Nullable String pickupLocation,
        @Nullable String pickupPincode,
        @Nullable String webhookToken,
        @Nullable String baseUrl,
        @Nullable BigDecimal weightKg,
        @Nullable BigDecimal lengthCm,
        @Nullable BigDecimal breadthCm,
        @Nullable BigDecimal heightCm) {

    ShiprocketProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://apiv2.shiprocket.in" : baseUrl;
        pickupLocation = pickupLocation == null || pickupLocation.isBlank() ? "Primary" : pickupLocation;
        weightKg = weightKg == null ? new BigDecimal("0.5") : weightKg;
        lengthCm = lengthCm == null ? new BigDecimal("30") : lengthCm;
        breadthCm = breadthCm == null ? new BigDecimal("25") : breadthCm;
        heightCm = heightCm == null ? new BigDecimal("5") : heightCm;
    }

    boolean configured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}

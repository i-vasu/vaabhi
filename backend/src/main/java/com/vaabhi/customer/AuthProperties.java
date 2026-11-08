package com.vaabhi.customer;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Customer sign-in settings under {@code vaabhi.auth}.
 *
 * @param googleClientId OAuth client id of the storefront's "Sign in with Google" button; Google sign-in answers
 *     503 while unset
 * @param accessTokenTtl lifetime of an access token (sent on every API call)
 * @param refreshTokenTtl how long a customer stays signed in without visiting; every visit renews it
 * @param refreshReuseGrace a just-rotated refresh token still works this long, so two tabs refreshing at once
 *     do not sign the customer out
 * @param otp one-time code settings
 */
@ConfigurationProperties("vaabhi.auth")
record AuthProperties(
        @Nullable String googleClientId,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration refreshReuseGrace,
        Otp otp) {

    AuthProperties {
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(60) : refreshTokenTtl;
        refreshReuseGrace = refreshReuseGrace == null ? Duration.ofSeconds(30) : refreshReuseGrace;
        otp = otp == null ? new Otp(null, 0, null, 0, 0) : otp;
    }

    boolean googleConfigured() {
        return googleClientId != null && !googleClientId.isBlank();
    }

    /**
     * @param ttl how long a code is valid
     * @param maxAttempts wrong guesses allowed per code
     * @param resendAfter minimum gap between codes to one number
     * @param maxPerPhonePerHour codes per number per hour
     * @param maxPerHour codes per hour across all numbers: caps the SMS bill if someone abuses the form
     */
    record Otp(Duration ttl, int maxAttempts, Duration resendAfter, int maxPerPhonePerHour, int maxPerHour) {

        Otp {
            ttl = ttl == null ? Duration.ofMinutes(5) : ttl;
            maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
            resendAfter = resendAfter == null ? Duration.ofSeconds(30) : resendAfter;
            maxPerPhonePerHour = maxPerPhonePerHour <= 0 ? 5 : maxPerPhonePerHour;
            maxPerHour = maxPerHour <= 0 ? 300 : maxPerHour;
        }
    }
}

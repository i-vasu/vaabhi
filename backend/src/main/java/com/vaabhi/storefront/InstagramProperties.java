package com.vaabhi.storefront;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The shop's Instagram account for the home page's reels section (Instagram API with Unified login). Without both
 * values the sync does nothing and the section stays hidden.
 *
 * @param accessToken the system-user token from the Meta app (Use cases → Instagram API → API setup with Unified
 *     login → Generate token); it does not expire
 * @param accountId the Instagram account's id as shown next to it on that page
 * @param limit how many of the best reels the home page shows
 */
@ConfigurationProperties("vaabhi.instagram")
record InstagramProperties(
        @Nullable String accessToken,
        @Nullable String accountId,
        @Nullable String baseUrl,
        @Nullable Integer limit) {

    InstagramProperties {
        accessToken = accessToken == null || accessToken.isBlank() ? null : accessToken.trim();
        accountId = accountId == null || accountId.isBlank() ? null : accountId.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://graph.facebook.com/v23.0" : baseUrl;
        limit = limit == null || limit < 1 ? 6 : limit;
    }

    boolean configured() {
        return accessToken != null && accountId != null;
    }
}

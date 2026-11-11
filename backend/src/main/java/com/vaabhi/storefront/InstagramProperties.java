package com.vaabhi.storefront;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The shop's Instagram account for the home page's reels section (Instagram API with Instagram Login). Without a
 * token the sync does nothing and the section stays hidden.
 *
 * @param accessToken a long-lived Instagram user token (Meta app → Instagram → API setup with Instagram login →
 *     Generate token). The sync refreshes it weekly and keeps the new one in the database, so this value is only
 *     the starting point; paste a fresh one here if the stored one ever stops working.
 * @param limit how many of the latest reels the home page shows
 */
@ConfigurationProperties("vaabhi.instagram")
record InstagramProperties(@Nullable String accessToken, @Nullable String baseUrl, @Nullable Integer limit) {

    InstagramProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://graph.instagram.com" : baseUrl;
        limit = limit == null || limit < 1 ? 8 : limit;
    }

    @Nullable
    String token() {
        return accessToken == null || accessToken.isBlank() ? null : accessToken.trim();
    }
}

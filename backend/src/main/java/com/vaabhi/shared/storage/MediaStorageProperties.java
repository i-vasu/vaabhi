package com.vaabhi.shared.storage;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2 settings. Without credentials the app still runs; photo uploads answer 503. Browsers load
 * photos from the bucket's custom domain (MEDIA_URL in the frontends), never through the API.
 */
@ConfigurationProperties("vaabhi.media")
public record MediaStorageProperties(
        @Nullable String accountId,
        @Nullable String accessKeyId,
        @Nullable String secretAccessKey,
        String bucket) {

    public MediaStorageProperties {
        bucket = bucket == null || bucket.isBlank() ? "vaabhi-media" : bucket;
    }

    boolean configured() {
        return notBlank(accountId) && notBlank(accessKeyId) && notBlank(secretAccessKey);
    }

    private static boolean notBlank(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}

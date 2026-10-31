package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/**
 * A photo or video in Cloudflare R2. {@code key} is the object key prefix; the storefront builds rendition URLs
 * from it (e.g. {@code <media-url>/<key>/800.webp}).
 */
public record MediaResponse(
        MediaKind kind,
        String key,
        String alt,
        @Nullable String colourName,
        @Nullable Integer width,
        @Nullable Integer height) {}

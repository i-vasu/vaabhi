package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/**
 * A product photo as the admin sees it. {@code key} is the R2 prefix; renditions live at
 * {@code <key>/400.webp}, {@code /800.webp} and {@code /1600.webp}. PENDING means the upload never finished.
 */
public record AdminMedia(
        long id,
        MediaKind kind,
        String key,
        String alt,
        @Nullable String colourName,
        @Nullable Integer width,
        @Nullable Integer height,
        MediaStatus status) {}

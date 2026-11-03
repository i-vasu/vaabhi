package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/**
 * A product photo or video as the admin sees it. {@code key} is the R2 prefix: a photo's renditions live at
 * {@code <key>/400.webp}, {@code /800.webp} and {@code /1600.webp}; a video at {@code <key>/video}, without width
 * or height. PENDING means the upload never finished.
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

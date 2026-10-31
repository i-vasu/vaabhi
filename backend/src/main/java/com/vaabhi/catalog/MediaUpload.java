package com.vaabhi.catalog;

import java.time.Instant;
import java.util.List;

/** Where to PUT each rendition (WebP), then call complete. The URLs expire at {@code expiresAt}. */
public record MediaUpload(AdminMedia media, List<Rendition> renditions, Instant expiresAt) {

    public record Rendition(int width, String uploadUrl) {}
}

package com.vaabhi.catalog;

import java.time.Instant;

/** Where to PUT the video file, then call complete. The URL expires at {@code expiresAt}. */
public record VideoUpload(AdminMedia media, String uploadUrl, Instant expiresAt) {}

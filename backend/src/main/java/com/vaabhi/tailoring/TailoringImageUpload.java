package com.vaabhi.tailoring;

import java.time.Instant;

/**
 * Where to upload a photo.
 *
 * @param key send this with the form once the upload finished
 * @param uploadUrl PUT the 400 px WebP here before {@code expiresAt}
 */
public record TailoringImageUpload(String key, String uploadUrl, Instant expiresAt) {}

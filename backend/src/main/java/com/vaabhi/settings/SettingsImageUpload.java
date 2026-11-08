package com.vaabhi.settings;

import java.time.Instant;

/**
 * Where to upload a settings image.
 *
 * @param key save this in the settings section once the upload finished
 * @param uploadUrl PUT the 400 px WebP here before {@code expiresAt}
 */
public record SettingsImageUpload(String key, String uploadUrl, Instant expiresAt) {}

package com.vaabhi.settings;

import org.jspecify.annotations.Nullable;

/**
 * Published after a settings section is saved, e.g. for the activity log.
 *
 * @param section the section's API name, e.g. {@code shop-profile}
 * @param title human wording, e.g. "Updated the shop profile"
 * @param actor who saved it (staff name), when known
 */
public record SettingsChanged(
        String section, String title, @Nullable String actor) {}

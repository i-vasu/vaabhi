package com.vaabhi.settings;

import jakarta.validation.constraints.NotNull;

/** Which settings image is being uploaded. */
public record SettingsImageRequest(@NotNull SettingsImage.Purpose purpose) {}

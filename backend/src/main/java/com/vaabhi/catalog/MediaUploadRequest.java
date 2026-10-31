package com.vaabhi.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Starts a photo upload. Width and height are those of the largest (1600 px) rendition. */
public record MediaUploadRequest(
        @NotBlank @Size(max = 200) String alt,
        @Nullable @Size(max = 60) String colourName,
        @Positive @Max(10_000) int width,
        @Positive @Max(10_000) int height) {}

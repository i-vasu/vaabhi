package com.vaabhi.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Starts a video upload. {@code contentType} is the file's type as the browser reports it; the browser sends the
 * same type with the PUT, so R2 serves the video with it.
 */
public record VideoUploadRequest(
        @NotBlank @Size(max = 200) String alt,
        @Nullable @Size(max = 60) String colourName,

        @NotBlank @Pattern(regexp = "video/(mp4|quicktime|webm)", message = "Use an MP4, MOV or WebM video")
        String contentType) {}

package com.vaabhi.gallery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** A design idea (DESIGN) or a finished garment (CUSTOMER) in the gallery. */
public record GalleryPhoto(
        long id,
        Kind kind,
        String title,
        @Nullable String tags,
        @Nullable Long templateId,
        @Nullable String garment,
        @Nullable Long clientId,
        @Nullable String clientName,
        @Nullable Long orderId,
        String photoKey,
        @Nullable String createdBy,
        OffsetDateTime createdAt) {

    public enum Kind {
        DESIGN,
        CUSTOMER
    }

    /**
     * Add or edit a gallery photo.
     *
     * @param photoKey from {@code POST /gallery/uploads}, once the file was uploaded
     * @param tags comma-separated words to search by, e.g. "boat neck, silk"
     */
    public record GalleryForm(
            @NotNull Kind kind,
            @NotBlank @Size(max = 120) String title,
            @Nullable @Size(max = 200) String tags,
            @Nullable Long templateId,
            @Nullable Long clientId,
            @Nullable Long orderId,
            @NotBlank @Size(max = 200) String photoKey) {}

    /**
     * Where to upload a gallery photo.
     *
     * @param uploadUrl PUT the 800 px WebP here before {@code expiresAt}
     */
    public record GalleryUpload(String key, String uploadUrl, Instant expiresAt) {}
}

package com.vaabhi.storefront;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A home-page banner.
 *
 * @param imageKey R2 key; renditions at {@code <key>/{400,800,1600}.webp}
 * @param link a storefront path such as {@code /c/sarees}
 * @param live active and inside its dates today (what the storefront shows)
 */
public record Banner(
        long id,
        Slot slot,
        int position,
        @Nullable String eyebrow,
        String title,
        @Nullable String subtitle,
        @Nullable String ctaLabel,
        @Nullable String link,
        String imageKey,
        @Nullable String altText,
        boolean active,
        @Nullable LocalDate startsOn,
        @Nullable LocalDate endsOn,
        boolean live) {

    /** Where on the home page. */
    public enum Slot {
        /** The big slides at the top. */
        HERO,
        /** "Shop by occasion" tiles. */
        OCCASION,
        /** The photo beside "The craft". */
        CRAFT
    }

    /** Add or edit a banner. */
    public record BannerForm(
            @NotNull Slot slot,
            @Nullable @Size(max = 60) String eyebrow,
            @NotBlank @Size(max = 120) String title,
            @Nullable @Size(max = 240) String subtitle,
            @Nullable @Size(max = 40) String ctaLabel,

            @Nullable
            @Size(max = 200)
            @Pattern(regexp = "^/[A-Za-z0-9/_\\-?=&%.]*$", message = "must be a storefront path like /c/sarees")
            String link,

            @NotBlank @Size(max = 200) String imageKey,
            @Nullable @Size(max = 200) String altText,
            boolean active,
            @Nullable LocalDate startsOn,
            @Nullable LocalDate endsOn) {}

    /** The order of a slot's banners, first to last. */
    public record BannerOrder(
            @NotNull Slot slot, @NotNull @Size(max = 50) List<@NotNull Long> ids) {}

    /**
     * Where to upload a storefront photo: PUT each size (a WebP that wide) to its URL, then save the key.
     *
     * @param renditions width → upload URL
     */
    public record SiteImageUpload(String key, List<Rendition> renditions, Instant expiresAt) {

        public record Rendition(int width, String uploadUrl) {}
    }

    /** Set or clear a category's photo. */
    public record CategoryImageForm(
            @Nullable @Size(max = 200) String imageKey) {}

    /**
     * What the storefront home page shows (live banners only, in order).
     *
     * @param craft the craft section photo, if one is live
     */
    public record HomeContent(
            List<Banner> hero,
            List<Banner> occasions,
            @Nullable Banner craft) {}
}

package com.vaabhi.storefront;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A reel from the shop's Instagram account, for the home page.
 *
 * @param permalink the reel on Instagram, where a tap takes the customer
 * @param imageKey R2 key of the cover, a JPEG served as is ({@code <media domain>/<imageKey>})
 * @param videoKey R2 key of the reel's MP4; may be missing for reels synced before videos were copied — the page then
 *     falls back to the cover
 */
public record InstagramReel(
        String id, String permalink, @Nullable String caption, String imageKey, String videoKey, Instant postedAt) {}

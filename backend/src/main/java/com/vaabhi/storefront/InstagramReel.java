package com.vaabhi.storefront;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A reel from the shop's Instagram account, for the home page.
 *
 * @param permalink the reel on Instagram, where a tap takes the customer
 * @param imageKey R2 key of the cover, a JPEG served as is ({@code <media domain>/<imageKey>})
 */
public record InstagramReel(
        String id, String permalink, @Nullable String caption, String imageKey, Instant postedAt) {}

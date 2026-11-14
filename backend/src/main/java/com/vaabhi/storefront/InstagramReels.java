package com.vaabhi.storefront;

import com.vaabhi.shared.storage.ObjectStorage;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The home page's Instagram reels. Every hour the account's reels are read and the most engaged few are kept,
 * each new cover (and video, when Instagram exposes one) copied once to R2, so the home page loads from our own
 * media domain and never waits on Instagram. Reels that drop out of the best few are removed with their files.
 */
@Service
@EnableConfigurationProperties(InstagramProperties.class)
class InstagramReels {

    private static final Logger log = LoggerFactory.getLogger(InstagramReels.class);
    private static final int CAPTION_MAX = 300;

    private final InstagramProperties properties;
    private final InstagramClient instagram;
    private final ObjectStorage storage;
    private final JdbcClient jdbc;

    InstagramReels(InstagramProperties properties, InstagramClient instagram, ObjectStorage storage, JdbcClient jdbc) {
        this.properties = properties;
        this.instagram = instagram;
        this.storage = storage;
        this.jdbc = jdbc;
    }

    /** The synced reels, newest first (the set is already the best few, so insertion order by recency is enough). */
    List<InstagramReel> latest() {
        return jdbc.sql("""
                        SELECT media_id AS id, permalink, caption, image_key,
                               'instagram/' || media_id || '.mp4' AS video_key, posted_at
                        FROM instagram_reel
                        ORDER BY posted_at DESC
                        LIMIT :limit
                        """)
                .param("limit", properties.limit())
                .query(InstagramReel.class)
                .list();
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    void scheduledSync() {
        try {
            sync();
        } catch (RuntimeException e) {
            log.warn("Instagram reels sync failed: {}", e.getMessage());
        }
    }

    void sync() {
        if (!properties.configured() || !storage.configured()) {
            return;
        }
        var reels = instagram
                .recentMedia(
                        Objects.requireNonNull(properties.accountId()),
                        Objects.requireNonNull(properties.accessToken()))
                .stream()
                .filter(m -> m.reel() && m.thumbnailUrl() != null)
                .sorted(Comparator.comparingLong(InstagramClient.Media::engagement)
                        .reversed()
                        .thenComparing(InstagramClient.Media::postedAt, Comparator.reverseOrder()))
                .limit(properties.limit())
                .toList();

        var have = new HashSet<>(jdbc.sql("SELECT media_id FROM instagram_reel")
                .query(String.class)
                .list());
        for (var reel : reels) {
            if (have.contains(reel.id())) {
                // Reels synced before videos were copied get theirs now (one cheap HEAD per kept reel).
                if (reel.mediaUrl() != null && !storage.exists(videoKey(reel.id()))) {
                    copyVideo(reel);
                }
                continue;
            }
            var key = "instagram/" + reel.id() + ".jpg";
            storage.put(key, instagram.download(Objects.requireNonNull(reel.thumbnailUrl())), "image/jpeg");
            if (reel.mediaUrl() != null) {
                copyVideo(reel);
            }
            jdbc.sql("""
                            INSERT INTO instagram_reel (media_id, permalink, caption, image_key, posted_at)
                            VALUES (:id, :permalink, :caption, :key, :postedAt)
                            ON CONFLICT (media_id) DO NOTHING
                            """)
                    .param("id", reel.id())
                    .param("permalink", reel.permalink())
                    .param("caption", caption(reel.caption()))
                    .param("key", key)
                    .param("postedAt", Timestamp.from(reel.postedAt()))
                    .update();
        }

        var keep = reels.stream().map(InstagramClient.Media::id).toList();
        for (var id : have) {
            if (!keep.contains(id)) {
                jdbc.sql("DELETE FROM instagram_reel WHERE media_id = :id RETURNING image_key")
                        .param("id", id)
                        .query(String.class)
                        .optional()
                        .ifPresent(storage::delete);
                storage.delete(videoKey(id));
            }
        }
    }

    private static String videoKey(String mediaId) {
        return "instagram/" + mediaId + ".mp4";
    }

    /** Copies a reel's MP4 to R2; a failure keeps the reel with its cover only (the page then shows no video). */
    private void copyVideo(InstagramClient.Media reel) {
        try {
            storage.put(videoKey(reel.id()), instagram.download(Objects.requireNonNull(reel.mediaUrl())), "video/mp4");
        } catch (RuntimeException e) {
            log.warn("Could not copy reel {} video; keeping the cover only: {}", reel.id(), e.getMessage());
        }
    }

    private static @Nullable String caption(@Nullable String caption) {
        if (caption == null) {
            return null;
        }
        var trimmed = caption.strip();
        return trimmed.length() <= CAPTION_MAX ? trimmed : trimmed.substring(0, CAPTION_MAX - 1) + "…";
    }
}

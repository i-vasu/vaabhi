package com.vaabhi.catalog;

import com.vaabhi.shared.storage.ObjectStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

/**
 * Product photos and videos in Cloudflare R2. The browser resizes each photo to {@link #RENDITIONS} and uploads
 * them straight to R2 with presigned URLs; a video goes up as is, to {@code <key>/video}. The server only records
 * the media and checks the files arrived.
 */
@Service
public class ProductMediaService {

    /** Rendition widths in pixels; the storefront requests {@code <key>/<width>.webp}. */
    public static final List<Integer> RENDITIONS = List.of(400, 800, 1600);

    static final int MAX_PHOTOS = 12;
    static final int MAX_VIDEOS = 3;
    /** Every photo and video of a product: the most a reorder can name. */
    static final int MAX_MEDIA = MAX_PHOTOS + MAX_VIDEOS;
    /** A video's object name under its key; R2 serves it with the type the browser uploaded it with. */
    static final String VIDEO_FILE = "video";

    private static final Duration UPLOAD_WINDOW = Duration.ofMinutes(15);
    /** Videos are larger and phones upload slowly; the URL only has to be valid when the PUT starts. */
    private static final Duration VIDEO_UPLOAD_WINDOW = Duration.ofMinutes(60);

    private final ProductMediaStore store;
    private final ObjectStorage storage;
    private final ApplicationEventPublisher events;

    ProductMediaService(ProductMediaStore store, ObjectStorage storage, ApplicationEventPublisher events) {
        this.store = store;
        this.storage = storage;
        this.events = events;
    }

    @Transactional
    public MediaUpload startUpload(long productId, MediaUploadRequest request) {
        requireStorage();
        requireProduct(productId);
        requireRoom(productId, MediaKind.IMAGE, MAX_PHOTOS, "A product can have at most " + MAX_PHOTOS + " photos");
        var key = newKey(productId);
        var mediaId = store.insertPending(
                productId,
                MediaKind.IMAGE,
                key,
                request.alt(),
                request.colourName(),
                request.width(),
                request.height());
        var renditions = RENDITIONS.stream()
                .map(width -> new MediaUpload.Rendition(
                        width,
                        storage.presignPut(renditionKey(key, width), UPLOAD_WINDOW)
                                .toString()))
                .toList();
        return new MediaUpload(
                store.find(productId, mediaId).orElseThrow(),
                renditions,
                Instant.now().plus(UPLOAD_WINDOW));
    }

    @Transactional
    public VideoUpload startVideoUpload(long productId, VideoUploadRequest request) {
        requireStorage();
        requireProduct(productId);
        requireRoom(productId, MediaKind.VIDEO, MAX_VIDEOS, "A product can have at most " + MAX_VIDEOS + " videos");
        var key = newKey(productId);
        var mediaId =
                store.insertPending(productId, MediaKind.VIDEO, key, request.alt(), request.colourName(), null, null);
        return new VideoUpload(
                store.find(productId, mediaId).orElseThrow(),
                storage.presignPut(videoKey(key), VIDEO_UPLOAD_WINDOW).toString(),
                Instant.now().plus(VIDEO_UPLOAD_WINDOW));
    }

    /** Marks a photo ready once every rendition is in storage, or a video once its file is. */
    @Transactional
    public List<AdminMedia> completeUpload(long productId, long mediaId) {
        var media = requireMedia(productId, mediaId);
        if (media.kind() == MediaKind.VIDEO) {
            if (!storage.exists(videoKey(media.key()))) {
                throw incomplete("the video file");
            }
        } else {
            var missing = RENDITIONS.stream()
                    .filter(width -> !storage.exists(renditionKey(media.key(), width)))
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            if (!missing.isEmpty()) {
                throw incomplete(missing + " px versions");
            }
        }
        store.markReady(mediaId);
        events.publishEvent(new CatalogChanged(productId));
        return store.list(productId);
    }

    /** Reorders the photos and video (list order) and updates alt text and colours. The list must name them all. */
    @Transactional
    public List<AdminMedia> update(long productId, List<MediaUpdate> updates) {
        requireProduct(productId);
        var existing = store.list(productId).stream().map(AdminMedia::id).collect(Collectors.toSet());
        var given = new HashSet<Long>();
        updates.forEach(u -> given.add(u.id()));
        if (given.size() != updates.size() || !given.equals(existing)) {
            throw new CatalogValidationException(
                    HttpStatus.BAD_REQUEST, "media", "Send every photo and video of the product exactly once");
        }
        store.update(productId, updates);
        events.publishEvent(new CatalogChanged(productId));
        return store.list(productId);
    }

    /** Removes the photo or video; its files are deleted from storage afterwards (best effort). */
    @Transactional
    public List<AdminMedia> delete(long productId, long mediaId) {
        var media = requireMedia(productId, mediaId);
        store.delete(productId, mediaId);
        if (storage.configured()) {
            if (media.kind() == MediaKind.VIDEO) {
                storage.delete(videoKey(media.key()));
            } else {
                RENDITIONS.forEach(width -> storage.delete(renditionKey(media.key(), width)));
                storage.delete(CatalogFeedImages.feedImageKey(media.key()));
            }
        }
        events.publishEvent(new CatalogChanged(productId));
        return store.list(productId);
    }

    static String renditionKey(String key, int width) {
        return key + "/" + width + ".webp";
    }

    static String videoKey(String key) {
        return key + "/" + VIDEO_FILE;
    }

    private static CatalogValidationException incomplete(String missing) {
        return new CatalogValidationException(HttpStatus.CONFLICT, "media", "Upload incomplete: missing " + missing);
    }

    private static String newKey(long productId) {
        return "products/" + productId + "/" + UUID.randomUUID();
    }

    private void requireStorage() {
        if (!storage.configured()) {
            throw new ErrorResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Photo storage is not configured on this server"),
                    null);
        }
    }

    private void requireRoom(long productId, MediaKind kind, int max, String message) {
        var count = store.list(productId).stream().filter(m -> m.kind() == kind).count();
        if (count >= max) {
            throw new CatalogValidationException(HttpStatus.BAD_REQUEST, "media", message);
        }
    }

    private void requireProduct(long productId) {
        if (!store.productExists(productId)) {
            throw new CatalogNotFoundException("Product", String.valueOf(productId));
        }
    }

    private AdminMedia requireMedia(long productId, long mediaId) {
        requireProduct(productId);
        return store.find(productId, mediaId)
                .orElseThrow(() -> new CatalogNotFoundException("Photo", String.valueOf(mediaId)));
    }
}

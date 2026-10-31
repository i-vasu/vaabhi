package com.vaabhi.catalog;

import com.vaabhi.shared.storage.ObjectStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

/**
 * Product photos in Cloudflare R2. The browser resizes each photo to {@link #RENDITIONS} and uploads them
 * straight to R2 with presigned URLs; the server only records the photo and checks the files arrived.
 */
@Service
public class ProductMediaService {

    /** Rendition widths in pixels; the storefront requests {@code <key>/<width>.webp}. */
    public static final List<Integer> RENDITIONS = List.of(400, 800, 1600);

    static final int MAX_PHOTOS = 12;
    private static final Duration UPLOAD_WINDOW = Duration.ofMinutes(15);

    private final ProductMediaStore store;
    private final ObjectStorage storage;

    ProductMediaService(ProductMediaStore store, ObjectStorage storage) {
        this.store = store;
        this.storage = storage;
    }

    @Transactional
    public MediaUpload startUpload(long productId, MediaUploadRequest request) {
        if (!storage.configured()) {
            throw new ErrorResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Photo storage is not configured on this server"),
                    null);
        }
        requireProduct(productId);
        if (store.list(productId).size() >= MAX_PHOTOS) {
            throw new CatalogValidationException(
                    HttpStatus.BAD_REQUEST, "media", "A product can have at most " + MAX_PHOTOS + " photos");
        }
        var key = "products/" + productId + "/" + UUID.randomUUID();
        var mediaId = store.insertPending(productId, key, request);
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

    /** Marks the photo ready once every rendition is in storage. */
    @Transactional
    public List<AdminMedia> completeUpload(long productId, long mediaId) {
        var media = requireMedia(productId, mediaId);
        var missing = RENDITIONS.stream()
                .filter(width -> !storage.exists(renditionKey(media.key(), width)))
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            throw new CatalogValidationException(
                    HttpStatus.CONFLICT, "media", "Upload incomplete: missing " + missing + " px versions");
        }
        store.markReady(mediaId);
        return store.list(productId);
    }

    /** Reorders the photos (list order) and updates alt text and colours. The list must name every photo. */
    @Transactional
    public List<AdminMedia> update(long productId, List<MediaUpdate> updates) {
        requireProduct(productId);
        var existing = store.list(productId).stream().map(AdminMedia::id).collect(Collectors.toSet());
        var given = new HashSet<Long>();
        updates.forEach(u -> given.add(u.id()));
        if (given.size() != updates.size() || !given.equals(existing)) {
            throw new CatalogValidationException(
                    HttpStatus.BAD_REQUEST, "media", "Send every photo of the product exactly once");
        }
        store.update(productId, updates);
        return store.list(productId);
    }

    /** Removes the photo; its files are deleted from storage afterwards (best effort). */
    @Transactional
    public List<AdminMedia> delete(long productId, long mediaId) {
        var media = requireMedia(productId, mediaId);
        store.delete(productId, mediaId);
        if (storage.configured()) {
            RENDITIONS.forEach(width -> storage.delete(renditionKey(media.key(), width)));
        }
        return store.list(productId);
    }

    static String renditionKey(String key, int width) {
        return key + "/" + width + ".webp";
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

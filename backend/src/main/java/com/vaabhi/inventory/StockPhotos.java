package com.vaabhi.inventory;

import com.vaabhi.shared.storage.ObjectStorage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reference photos of a stock lot. The browser resizes to a 400 px WebP and PUTs it to {@code <key>/400.webp}
 * with the presigned URL, then saves the key here.
 */
@Service
class StockPhotos {

    static final int MAX = 6;
    private static final String PREFIX = "stock/";
    private static final Duration WINDOW = Duration.ofMinutes(15);

    /** Where to PUT the photo, and the key to save afterwards. */
    public record Upload(String key, String uploadUrl, Instant expiresAt) {}

    /** The key of an uploaded photo. */
    public record PhotoForm(@NotBlank @Size(max = 120) String key) {}

    private final JdbcClient jdbc;
    private final ObjectStorage storage;
    private final InventoryService inventory;

    StockPhotos(JdbcClient jdbc, ObjectStorage storage, InventoryService inventory) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.inventory = inventory;
    }

    Upload startUpload(long itemId) {
        inventory.get(itemId);
        if (!storage.configured()) {
            throw new ErrorResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Photo storage is not configured on this server"),
                    null);
        }
        var key = PREFIX + UUID.randomUUID();
        return new Upload(
                key,
                storage.presignPut(file(key), WINDOW).toString(),
                Instant.now().plus(WINDOW));
    }

    @Transactional
    StockItem add(long itemId, PhotoForm form) {
        var item = inventory.get(itemId);
        var key = form.key().trim();
        if (!key.matches("^" + PREFIX + "[0-9a-f-]{36}$") || !storage.exists(file(key))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload the photo again");
        }
        if (item.photos().size() >= MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Up to " + MAX + " photos");
        }
        jdbc.sql("""
                        INSERT INTO stock_item_photo (stock_item_id, photo_key, position)
                        SELECT :item, :key, COALESCE(MAX(position), -1) + 1 FROM stock_item_photo
                        WHERE stock_item_id = :item
                        """).param("item", itemId).param("key", key).update();
        return inventory.get(itemId);
    }

    /** Takes the photo off the item. The file stays in storage so cached pages don't break. */
    @Transactional
    StockItem remove(long itemId, long photoId) {
        inventory.get(itemId);
        if (jdbc.sql("DELETE FROM stock_item_photo WHERE id = :id AND stock_item_id = :item")
                        .param("id", photoId)
                        .param("item", itemId)
                        .update()
                == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
        }
        return inventory.get(itemId);
    }

    private static String file(String key) {
        return key + "/400.webp";
    }
}

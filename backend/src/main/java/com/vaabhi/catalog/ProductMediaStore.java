package com.vaabhi.catalog;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** SQL for product photos. */
@Component
class ProductMediaStore {

    private static final String PRODUCT_ID = "productId";

    private final JdbcClient jdbc;

    ProductMediaStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<AdminMedia> list(long productId) {
        return jdbc.sql("""
                        SELECT id, kind, storage_key AS key, alt_text AS alt, colour_name, width, height, status
                        FROM product_media WHERE product_id = :productId ORDER BY position, id
                        """)
                .param(PRODUCT_ID, productId)
                .query(AdminMedia.class)
                .list();
    }

    Optional<AdminMedia> find(long productId, long mediaId) {
        return list(productId).stream().filter(m -> m.id() == mediaId).findFirst();
    }

    boolean productExists(long productId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM product WHERE id = :productId)")
                .param(PRODUCT_ID, productId)
                .query(Boolean.class)
                .single();
    }

    long insertPending(long productId, String key, MediaUploadRequest request) {
        return jdbc.sql("""
                        INSERT INTO product_media (product_id, kind, storage_key, colour_name, alt_text, width, height,
                                                   position, status)
                        VALUES (:productId, 'IMAGE', :key, :colour, :alt, :width, :height,
                                (SELECT COALESCE(MAX(position), -1) + 1 FROM product_media WHERE product_id = :productId),
                                'PENDING')
                        RETURNING id
                        """)
                .param(PRODUCT_ID, productId)
                .param("key", key)
                .param("colour", blankToNull(request.colourName()))
                .param("alt", request.alt().trim())
                .param("width", request.width())
                .param("height", request.height())
                .query(Long.class)
                .single();
    }

    void markReady(long mediaId) {
        jdbc.sql("UPDATE product_media SET status = 'READY' WHERE id = :id")
                .param("id", mediaId)
                .update();
        touchProductOf(mediaId);
    }

    void update(long productId, List<MediaUpdate> updates) {
        for (var i = 0; i < updates.size(); i++) {
            var u = updates.get(i);
            jdbc.sql("""
                            UPDATE product_media SET position = :position, alt_text = :alt, colour_name = :colour
                            WHERE id = :id AND product_id = :productId
                            """)
                    .param("position", i)
                    .param("alt", u.alt().trim())
                    .param("colour", blankToNull(u.colourName()))
                    .param("id", u.id())
                    .param(PRODUCT_ID, productId)
                    .update();
        }
        touchProduct(productId);
    }

    void delete(long productId, long mediaId) {
        jdbc.sql("DELETE FROM product_media WHERE id = :id AND product_id = :productId")
                .param("id", mediaId)
                .param(PRODUCT_ID, productId)
                .update();
        touchProduct(productId);
    }

    private void touchProductOf(long mediaId) {
        jdbc.sql(
                        "UPDATE product SET updated_at = now() WHERE id = (SELECT product_id FROM product_media WHERE id = :id)")
                .param("id", mediaId)
                .update();
    }

    private void touchProduct(long productId) {
        jdbc.sql("UPDATE product SET updated_at = now() WHERE id = :productId")
                .param(PRODUCT_ID, productId)
                .update();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

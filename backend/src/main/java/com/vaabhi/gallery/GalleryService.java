package com.vaabhi.gallery;

import com.vaabhi.shared.storage.ObjectStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gallery photos. The browser resizes the photo to an 800 px WebP and uploads it to {@code <key>/800.webp} with
 * the presigned URL; the key is then saved with the photo's details.
 */
@Service
public class GalleryService {

    static final int WIDTH = 800;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String PREFIX = "gallery/";
    private static final String SELECT = """
            SELECT g.id, g.kind, g.title, g.tags, g.template_id, t.name AS garment, g.client_id,
                   c.first_name || COALESCE(' ' || c.last_name, '') AS client_name, g.order_id, g.photo_key,
                   g.created_by, g.created_at
            FROM gallery_photo g LEFT JOIN garment_template t ON t.id = g.template_id
            LEFT JOIN client c ON c.id = g.client_id
            """;

    private final JdbcClient jdbc;
    private final ObjectStorage storage;

    GalleryService(JdbcClient jdbc, ObjectStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    GalleryPhoto.GalleryUpload startUpload() {
        if (!storage.configured()) {
            throw new ErrorResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Photo storage is not configured on this server"),
                    null);
        }
        var key = PREFIX + UUID.randomUUID();
        return new GalleryPhoto.GalleryUpload(
                key,
                storage.presignPut(file(key), WINDOW).toString(),
                Instant.now().plus(WINDOW));
    }

    private static String file(String key) {
        return key + "/" + WIDTH + ".webp";
    }

    /** Newest first, optionally of one kind, garment or customer, or matching a search of title and tags. */
    @Transactional(readOnly = true)
    public List<GalleryPhoto> list(
            GalleryPhoto.@Nullable Kind kind, @Nullable Long templateId, @Nullable Long clientId, @Nullable String q) {
        return jdbc.sql(SELECT + """
                        WHERE (CAST(:kind AS varchar) IS NULL OR g.kind = :kind)
                          AND (CAST(:template AS bigint) IS NULL OR g.template_id = :template)
                          AND (CAST(:client AS bigint) IS NULL OR g.client_id = :client)
                          AND (CAST(:q AS varchar) IS NULL OR lower(g.title) LIKE :q OR lower(g.tags) LIKE :q)
                        ORDER BY g.created_at DESC, g.id DESC LIMIT 300
                        """)
                .param("kind", kind == null ? null : kind.name())
                .param("template", templateId)
                .param("client", clientId)
                .param(
                        "q",
                        q == null || q.isBlank()
                                ? null
                                : "%" + q.trim().toLowerCase().replace("%", "\\%") + "%")
                .query(GalleryPhoto.class)
                .list();
    }

    private GalleryPhoto get(long id) {
        return jdbc.sql(SELECT + " WHERE g.id = :id")
                .param("id", id)
                .query(GalleryPhoto.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
    }

    @Transactional
    public GalleryPhoto save(@Nullable Long id, GalleryPhoto.GalleryForm form, @Nullable String actor) {
        if (id == null || !get(id).photoKey().equals(form.photoKey())) {
            if (!form.photoKey().matches("^" + PREFIX + "[0-9a-f-]{36}$") || !storage.exists(file(form.photoKey()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload the photo again");
            }
        }
        var sql = id == null ? """
                  INSERT INTO gallery_photo (kind, title, tags, template_id, client_id, order_id, photo_key, created_by)
                  VALUES (:kind, :title, :tags, :template, :client, :order, :key, :by) RETURNING id
                  """ : """
                  UPDATE gallery_photo SET kind = :kind, title = :title, tags = :tags, template_id = :template,
                      client_id = :client, order_id = :order, photo_key = :key
                  WHERE id = :id RETURNING id
                  """;
        long saved = jdbc.sql(sql)
                .param("kind", form.kind().name())
                .param("title", form.title().trim())
                .param(
                        "tags",
                        form.tags() == null || form.tags().isBlank()
                                ? null
                                : form.tags().trim())
                .param("template", form.templateId())
                .param("client", form.clientId())
                .param("order", form.orderId())
                .param("key", form.photoKey())
                .param("by", actor)
                .param("id", id)
                .query(Long.class)
                .single();
        return get(saved);
    }

    @Transactional
    public void remove(long id) {
        var photo = get(id);
        jdbc.sql("DELETE FROM gallery_photo WHERE id = :id").param("id", id).update();
        storage.delete(file(photo.photoKey()));
    }
}

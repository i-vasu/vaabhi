package com.vaabhi.catalog;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import com.vaabhi.shared.storage.ObjectStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Meta catalogues (Instagram Shopping, WhatsApp) take JPEG photos, but the shop stores WebP renditions. Every few
 * minutes this copies the largest rendition of each published product's photos to {@code <key>/feed.jpg}, a few at
 * a time so the small VPS is never busy for long. The feed only lists photos whose copy is done.
 */
@Component
class CatalogFeedImages {

    private static final Logger log = LoggerFactory.getLogger(CatalogFeedImages.class);
    private static final int BATCH = 10;
    private static final float QUALITY = 0.88f;

    private final JdbcClient jdbc;
    private final ObjectStorage storage;

    CatalogFeedImages(JdbcClient jdbc, ObjectStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    static String feedImageKey(String key) {
        return key + "/feed.jpg";
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT5M")
    void copyPending() {
        if (!storage.configured()) {
            return;
        }
        record Pending(long id, String storageKey) {}
        var pending = jdbc.sql("""
                        SELECT m.id, m.storage_key
                        FROM product_media m
                        JOIN product p ON p.id = m.product_id
                        WHERE m.kind = 'IMAGE' AND m.status = 'READY' AND NOT m.feed_image AND p.status = 'PUBLISHED'
                        ORDER BY p.published_at DESC, m.position, m.id
                        LIMIT :limit
                        """).param("limit", BATCH).query(Pending.class).list();
        for (var media : pending) {
            try {
                var largest = ProductMediaService.RENDITIONS.getLast();
                var jpeg = toJpeg(storage.get(ProductMediaService.renditionKey(media.storageKey(), largest)));
                storage.put(feedImageKey(media.storageKey()), jpeg, "image/jpeg");
                jdbc.sql("UPDATE product_media SET feed_image = TRUE WHERE id = :id")
                        .param("id", media.id())
                        .update();
            } catch (RuntimeException | IOException e) {
                log.warn("Could not make the catalogue JPEG for photo {}: {}", media.id(), e.getMessage());
            }
        }
    }

    static byte[] toJpeg(byte[] image) throws IOException {
        // The WebP reader is used directly: ImageIO's plugin lookup can miss it inside the Spring Boot jar.
        var reader = new WebPImageReaderSpi().createReaderInstance();
        BufferedImage source;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            reader.setInput(input);
            source = reader.read(0);
        } finally {
            reader.dispose();
        }
        // JPEG has no transparency: draw onto white so a transparent edge does not turn black.
        var rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = rgb.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var out = new ByteArrayOutputStream();
        try (var stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            var param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}

package com.vaabhi.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the Meta catalogue (Instagram Shopping + WhatsApp) in step with the shop, instantly: right after a product
 * is published, edited, unpublished or its photos change in the admin, and every minute for everything else (stock
 * sold online or in the shop). Each run first makes any missing JPEG photo copies, then sends Meta only the items
 * whose fields changed since the last send, and deletes items that are no longer for sale. Without the Catalog API
 * settings it only makes the JPEG copies, and Meta reads the scheduled CSV feed instead.
 */
@Component
@EnableConfigurationProperties(MetaCatalogProperties.class)
class MetaCatalogSync {

    private static final Logger log = LoggerFactory.getLogger(MetaCatalogSync.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Meta takes up to 5,000 requests per batch; smaller batches keep each call quick. */
    private static final int BATCH = 500;
    /** JPEG copies per run before syncing, so one big upload does not hold the sync back for long. */
    private static final int MAX_IMAGE_ROUNDS = 5;

    private final MetaCatalogFeed feed;
    private final CatalogFeedImages images;
    private final MetaCatalogClient client;
    private final MetaCatalogProperties properties;
    private final JdbcClient jdbc;
    private final ReentrantLock running = new ReentrantLock();
    private final AtomicBoolean again = new AtomicBoolean();

    MetaCatalogSync(
            MetaCatalogFeed feed,
            CatalogFeedImages images,
            MetaCatalogClient client,
            MetaCatalogProperties properties,
            JdbcClient jdbc) {
        this.feed = feed;
        this.images = images;
        this.client = client;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    /** After the admin's change is committed, sync in the background so the admin's request is not slowed down. */
    @TransactionalEventListener(fallbackExecution = true)
    void onCatalogChanged(CatalogChanged event) {
        Thread.ofVirtual().name("meta-catalog-sync").start(this::syncNow);
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    void scheduled() {
        syncNow();
    }

    /** Runs a sync; a request that arrives while one runs makes it go round once more instead of waiting. */
    void syncNow() {
        again.set(true);
        while (again.get() && running.tryLock()) {
            try {
                again.set(false);
                syncOnce();
            } catch (RuntimeException e) {
                log.warn("Meta catalogue sync failed: {}", e.getMessage());
            } finally {
                running.unlock();
            }
        }
    }

    private void syncOnce() {
        for (int round = 0; round < MAX_IMAGE_ROUNDS && images.copyPending() == CatalogFeedImages.BATCH; round++) {
            // keep going while full batches were pending
        }
        if (properties.configured()) {
            push();
        }
    }

    private record Synced(String sku, String hash) {}

    private void push() {
        var stored = new HashMap<String, String>();
        jdbc.sql("SELECT sku, hash FROM meta_catalog_item")
                .query(Synced.class)
                .list()
                .forEach(s -> stored.put(s.sku(), s.hash()));

        var changed = new ArrayList<Pending>();
        var current = new HashSet<String>();
        for (var item : feed.items()) {
            current.add(item.id());
            var hash = hash(item.fields());
            if (!hash.equals(stored.get(item.id()))) {
                changed.add(new Pending(item.id(), item.productId(), hash, "UPDATE", item.fields()));
            }
        }
        stored.keySet().stream()
                .filter(sku -> !current.contains(sku))
                .forEach(sku -> changed.add(new Pending(sku, 0, "", "DELETE", Map.of("id", sku))));
        if (changed.isEmpty()) {
            return;
        }

        for (int from = 0; from < changed.size(); from += BATCH) {
            var chunk = changed.subList(from, Math.min(from + BATCH, changed.size()));
            client.send(chunk.stream()
                    .map(p -> new MetaCatalogClient.Request(p.method(), p.data()))
                    .toList());
            chunk.forEach(this::remember);
        }
        log.info("Meta catalogue: sent {} change(s)", changed.size());
    }

    private record Pending(String sku, long productId, String hash, String method, Map<String, Object> data) {}

    private void remember(Pending sent) {
        if (sent.method().equals("DELETE")) {
            jdbc.sql("DELETE FROM meta_catalog_item WHERE sku = :sku")
                    .param("sku", sent.sku())
                    .update();
            return;
        }
        jdbc.sql("""
                        INSERT INTO meta_catalog_item (sku, product_id, hash, synced_at)
                        VALUES (:sku, :productId, :hash, now())
                        ON CONFLICT (sku) DO UPDATE
                        SET product_id = EXCLUDED.product_id, hash = EXCLUDED.hash, synced_at = now()
                        """)
                .param("sku", sent.sku())
                .param("productId", sent.productId())
                .param("hash", sent.hash())
                .update();
    }

    static String hash(Map<String, Object> fields) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

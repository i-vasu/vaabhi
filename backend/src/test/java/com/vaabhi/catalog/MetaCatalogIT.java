package com.vaabhi.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.storage.ObjectStorage;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Meta catalogue end to end against real Postgres: the CSV feed, the JPEG photo copies and the instant
 * Catalog API sync, with Meta's Graph API replaced by a local HTTP server that records each batch.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, MetaCatalogIT.Storage.class})
class MetaCatalogIT {

    /** A 4×3 half-transparent WebP, as the browser uploads renditions. */
    private static final byte[] WEBP = Base64.getDecoder()
            .decode("UklGRlwAAABXRUJQVlA4WAoAAAAQAAAAAwAAAgAAQUxQSAoAAAABB1DAiAhERP8DVlA4ICwAAACQAQCdASoEAAMAAUAmJaACdL"
                    + "oAA5gA/vD6K67xaUa/+8Z//cZ//cZ/7uAAAA==");

    private static final String SLUG = "jamdani-cotton-saree";
    private static final String FEED = "/api/v1/store/catalog/meta-feed.csv";

    private static final List<String> BATCHES = new CopyOnWriteArrayList<>();
    private static final List<String> PATHS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger META_STATUS = new AtomicInteger(200);
    private static final HttpServer META = startMeta();

    private static HttpServer startMeta() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                PATHS.add(exchange.getRequestURI().getPath());
                BATCHES.add(URLDecoder.decode(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));
                var body = "{\"handles\":[\"h1\"]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(META_STATUS.get(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void meta(DynamicPropertyRegistry registry) {
        registry.add(
                "vaabhi.meta-catalog.base-url",
                () -> "http://localhost:" + META.getAddress().getPort());
        registry.add("vaabhi.meta-catalog.catalog-id", () -> "1674989461074504");
        registry.add("vaabhi.meta-catalog.access-token", () -> "test-token");
    }

    @AfterAll
    static void stopMeta() {
        META.stop(0);
    }

    /** Keeps the bytes the server stores and downloads (JPEG copies, renditions). */
    @TestConfiguration
    static class Storage {
        @Bean
        @Primary
        BytesStorage bytesStorage() {
            return new BytesStorage();
        }
    }

    static class BytesStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        volatile boolean configured = true;

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public URI presignPut(String key, Duration validFor) {
            return URI.create("https://r2.test/" + key);
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public void put(String key, byte[] bytes, String contentType) {
            objects.put(key, bytes);
        }

        @Override
        public byte[] get(String key) {
            var bytes = objects.get(key);
            if (bytes == null) {
                throw new IllegalStateException("missing " + key);
            }
            return bytes;
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private BytesStorage storage;

    @Autowired
    private CatalogFeedImages images;

    @Autowired
    private MetaCatalogSync sync;

    @BeforeEach
    void reset() {
        BATCHES.clear();
        PATHS.clear();
        META_STATUS.set(200);
        storage.configured = true;
        jdbc.sql("UPDATE product SET status = 'PUBLISHED' WHERE slug = :slug")
                .param("slug", SLUG)
                .update();
    }

    private long productId() {
        return jdbc.sql("SELECT id FROM product WHERE slug = :slug")
                .param("slug", SLUG)
                .query(Long.class)
                .single();
    }

    private List<String> skus() {
        return jdbc.sql("SELECT sku FROM product_variant WHERE product_id = :id AND active ORDER BY position, id")
                .param("id", productId())
                .query(String.class)
                .list();
    }

    private long photo(String key, boolean feedImage) {
        return jdbc.sql("""
                        INSERT INTO product_media (product_id, kind, storage_key, alt_text, position, status, feed_image)
                        VALUES (:product, 'IMAGE', :key, 'Saree', 0, 'READY', :feed) RETURNING id
                        """)
                .param("product", productId())
                .param("key", key)
                .param("feed", feedImage)
                .query(Long.class)
                .single();
    }

    private boolean feedImage(long mediaId) {
        return jdbc.sql("SELECT feed_image FROM product_media WHERE id = :id")
                .param("id", mediaId)
                .query(Boolean.class)
                .single();
    }

    private List<String> syncedSkus() {
        return jdbc.sql("SELECT sku FROM meta_catalog_item WHERE product_id = :id ORDER BY sku")
                .param("id", productId())
                .query(String.class)
                .list();
    }

    @Test
    void feedListsEveryVariantOfAProductWithAJpegPhoto() throws Exception {
        photo("products/" + SLUG + "/feed-a", true);
        var sku = skus().getFirst();

        var csv = mockMvc.perform(get(FEED))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        var lines = csv.split("\n");
        assertThat(lines[0]).startsWith("\"id\",\"item_group_id\",\"title\"").endsWith("\"product_type\"");
        assertThat(csv)
                .contains("\"" + sku + "\",\"" + SLUG + "\",\"Jamdani cotton saree\"")
                .contains("/p/" + SLUG)
                .contains("products/" + SLUG + "/feed-a/feed.jpg")
                .contains(" INR\"")
                .doesNotContain("velvet-bridal-lehenga-zardozi");
        assertThat(lines).hasSizeGreaterThanOrEqualTo(1 + skus().size());
    }

    @Test
    void feedFormatsMoneyAndQuotesCells() {
        assertThat(MetaCatalogFeed.money(new BigDecimal("3600"))).isEqualTo("3600.00 INR");
        assertThat(MetaCatalogFeed.money(new BigDecimal("99.999"))).isEqualTo("100.00 INR");
        assertThat(MetaCatalogFeed.cell("Silk, \"pure\"")).isEqualTo("\"Silk, \"\"pure\"\"\"");
    }

    @Test
    void copiesTheLargestRenditionToAJpegAndSkipsBrokenPhotos() {
        var good = photo("products/" + SLUG + "/copy-good", false);
        var broken = photo("products/" + SLUG + "/copy-broken", false);
        storage.objects.put("products/" + SLUG + "/copy-good/" + CatalogFeedImages.SOURCE_WIDTH + ".webp", WEBP);

        assertThat(images.copyPending()).isGreaterThanOrEqualTo(2);

        var jpeg = storage.objects.get("products/" + SLUG + "/copy-good/feed.jpg");
        assertThat(jpeg).isNotNull();
        assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
        assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
        assertThat(feedImage(good)).isTrue();
        assertThat(feedImage(broken)).isFalse();

        storage.configured = false;
        assertThat(images.copyPending()).isZero();
    }

    @Test
    void refusesAnImageThatIsNotWebp() {
        assertThatThrownBy(() -> CatalogFeedImages.toJpeg(new byte[] {1, 2, 3})).isInstanceOf(Exception.class);
    }

    @Test
    void sendsOnlyChangesToMetaAndDeletesWhatIsNoLongerForSale() {
        photo("products/" + SLUG + "/sync-a", true);
        var skus = skus();

        sync.syncNow();
        assertThat(PATHS).contains("/1674989461074504/items_batch");
        var batch = String.join("\n", BATCHES);
        assertThat(batch)
                .contains("item_type=PRODUCT_ITEM")
                .contains("allow_upsert=true")
                .contains("access_token=test-token")
                .contains("\"method\":\"UPDATE\"")
                .contains("\"id\":\"" + skus.getFirst() + "\"");
        assertThat(syncedSkus()).containsExactlyInAnyOrderElementsOf(skus);

        // Nothing changed: nothing is sent.
        BATCHES.clear();
        sync.syncNow();
        assertThat(BATCHES).isEmpty();

        // Archived: every variant is deleted from Meta and forgotten here.
        jdbc.sql("UPDATE product SET status = 'ARCHIVED' WHERE slug = :slug")
                .param("slug", SLUG)
                .update();
        sync.syncNow();
        assertThat(String.join("\n", BATCHES))
                .contains("\"method\":\"DELETE\"")
                .contains("\"id\":\"" + skus.getFirst() + "\"");
        assertThat(syncedSkus()).isEmpty();
    }

    @Test
    void aRefusedBatchIsRetriedOnTheNextRun() {
        photo("products/" + SLUG + "/refused-a", true);
        jdbc.sql("DELETE FROM meta_catalog_item").update();
        META_STATUS.set(400);

        sync.syncNow();
        assertThat(BATCHES).isNotEmpty();
        assertThat(syncedSkus()).isEmpty();

        META_STATUS.set(200);
        sync.syncNow();
        assertThat(syncedSkus()).isNotEmpty();
    }

    @Test
    void hashesTheSameFieldsTheSameWay() {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("id", "SKU-1");
        fields.put("price", "100.00 INR");
        Map<String, Object> same = new java.util.LinkedHashMap<>(fields);
        assertThat(MetaCatalogSync.hash(fields)).hasSize(64).isEqualTo(MetaCatalogSync.hash(same));
        same.put("price", "90.00 INR");
        assertThat(MetaCatalogSync.hash(same)).isNotEqualTo(MetaCatalogSync.hash(fields));
    }

    @Test
    void needsBothTheCatalogueAndTheTokenToPush() {
        assertThat(new MetaCatalogProperties(" ", "t", null).configured()).isFalse();
        assertThat(new MetaCatalogProperties("1", "", null).configured()).isFalse();
        var configured = new MetaCatalogProperties(" 1 ", " t ", "");
        assertThat(configured.configured()).isTrue();
        assertThat(configured.catalogId()).isEqualTo("1");
        assertThat(configured.baseUrl()).startsWith("https://graph.facebook.com/");
    }
}

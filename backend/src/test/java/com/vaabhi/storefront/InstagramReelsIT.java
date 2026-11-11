package com.vaabhi.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.storage.ObjectStorage;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The hourly reels sync against real Postgres, with Instagram and R2 replaced by fakes: new reels are stored with
 * their covers copied once, reels that drop out are removed with their covers, and the storefront lists them.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class InstagramReelsIT {

    private static final InstagramProperties CONFIGURED = new InstagramProperties("token", "17841", null, 2);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mockMvc;

    private final FakeInstagram instagram = new FakeInstagram();
    private final FakeStorage storage = new FakeStorage();

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM instagram_reel").update();
    }

    private InstagramReels reels(InstagramProperties properties) {
        return new InstagramReels(properties, instagram, storage, jdbc);
    }

    private static InstagramClient.Media reel(String id, String postedAt, @Nullable String caption) {
        return new InstagramClient.Media(
                id,
                true,
                "https://cdn.test/" + id + ".jpg",
                "https://instagram.com/reel/" + id,
                caption,
                Instant.parse(postedAt));
    }

    @Test
    void storesTheLatestReelsWithTheirCoversCopiedOnce() throws Exception {
        instagram.media = List.of(
                reel("3", "2026-11-03T10:00:00Z", "  Festive edit  "),
                new InstagramClient.Media(
                        "photo",
                        false,
                        null,
                        "https://instagram.com/p/photo",
                        null,
                        Instant.parse("2026-11-02T00:00:00Z")),
                new InstagramClient.Media(
                        "no-cover",
                        true,
                        null,
                        "https://instagram.com/reel/x",
                        null,
                        Instant.parse("2026-11-02T00:00:00Z")),
                reel("2", "2026-11-02T10:00:00Z", "x".repeat(400)),
                reel("1", "2026-11-01T10:00:00Z", null));
        var sync = reels(CONFIGURED);

        sync.sync();

        assertThat(storage.objects).containsOnlyKeys("instagram/3.jpg", "instagram/2.jpg");
        assertThat(storage.contentTypes).containsOnly("image/jpeg");
        var latest = sync.latest();
        assertThat(latest).extracting(InstagramReel::id).containsExactly("3", "2");
        assertThat(latest.get(0).caption()).isEqualTo("Festive edit");
        assertThat(latest.get(0).imageKey()).isEqualTo("instagram/3.jpg");
        assertThat(latest.get(0).permalink()).isEqualTo("https://instagram.com/reel/3");
        assertThat(latest.get(0).postedAt()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
        assertThat(latest.get(1).caption()).hasSize(300).endsWith("…");

        sync.sync();
        assertThat(instagram.downloads).containsExactly("https://cdn.test/3.jpg", "https://cdn.test/2.jpg");

        mockMvc.perform(get("/api/v1/store/instagram"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("3"))
                .andExpect(jsonPath("$[0].imageKey").value("instagram/3.jpg"))
                .andExpect(jsonPath("$[1].id").value("2"));
    }

    @Test
    void removesReelsThatDropOutWithTheirCovers() {
        var sync = reels(CONFIGURED);
        instagram.media = List.of(reel("2", "2026-11-02T10:00:00Z", null), reel("1", "2026-11-01T10:00:00Z", null));
        sync.sync();

        instagram.media = List.of(reel("4", "2026-11-04T10:00:00Z", "New"), reel("2", "2026-11-02T10:00:00Z", null));
        sync.sync();

        assertThat(sync.latest()).extracting(InstagramReel::id).containsExactly("4", "2");
        assertThat(storage.objects).containsOnlyKeys("instagram/4.jpg", "instagram/2.jpg");
        assertThat(storage.deleted).containsExactly("instagram/1.jpg");
    }

    @Test
    void doesNothingUntilInstagramAndStorageAreSetUp() {
        instagram.media = List.of(reel("1", "2026-11-01T10:00:00Z", null));

        reels(new InstagramProperties(null, null, null, null)).sync();
        storage.configured = false;
        reels(CONFIGURED).sync();

        assertThat(instagram.calls).isZero();
        assertThat(reels(CONFIGURED).latest()).isEmpty();
    }

    @Test
    void aFailedScheduledSyncIsLoggedNotThrown() {
        instagram.failure = new IllegalStateException("Instagram answered 400 for /17841/media");

        reels(CONFIGURED).scheduledSync();

        assertThat(reels(CONFIGURED).latest()).isEmpty();
    }

    /** Answers with {@link #media} and records the cover downloads. */
    static class FakeInstagram extends InstagramClient {
        List<Media> media = List.of();
        final List<String> downloads = new ArrayList<>();
        int calls;

        @Nullable
        RuntimeException failure;

        FakeInstagram() {
            super(new InstagramProperties(null, null, null, null));
        }

        @Override
        List<Media> recentMedia(String accountId, String token) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return media;
        }

        @Override
        byte[] download(String url) {
            downloads.add(url);
            return new byte[] {1, 2, 3};
        }
    }

    /** Stands in for R2: records uploads and deletes. */
    static class FakeStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        final List<String> contentTypes = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        boolean configured = true;

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
            contentTypes.add(contentType);
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
            deleted.add(key);
        }
    }
}

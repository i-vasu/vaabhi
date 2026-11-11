package com.vaabhi.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Graph API calls against a local HTTP server standing in for graph.facebook.com and Instagram's CDN. */
class InstagramClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private volatile int status = 200;
    private volatile byte[] body = new byte[0];

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastQuery.set(exchange.getRequestURI().getPath() + "?"
                    + exchange.getRequestURI().getRawQuery());
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private InstagramClient client() {
        return new InstagramClient(new InstagramProperties("token", "17841", baseUrl(), null));
    }

    private void respond(int status, String json) {
        this.status = status;
        this.body = json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void readsRecentMediaAndMarksReels() {
        respond(200, """
                {"data": [
                  {"id": "1", "media_type": "VIDEO", "media_product_type": "REELS",
                   "thumbnail_url": "https://cdn.test/1.jpg", "permalink": "https://instagram.com/reel/1",
                   "caption": "New sarees", "timestamp": "2026-11-01T10:15:30+0000"},
                  {"id": "2", "media_type": "IMAGE", "media_product_type": "FEED",
                   "permalink": "https://instagram.com/p/2", "caption": "  ", "timestamp": "2026-10-30T08:00:00+0000"},
                  {"id": "3", "permalink": "https://instagram.com/p/3"},
                  {"permalink": "https://instagram.com/p/4", "timestamp": "2026-10-29T08:00:00+0000"}
                ]}
                """);

        var media = client().recentMedia("17841", "secret token");

        assertThat(media).hasSize(2);
        var reel = media.get(0);
        assertThat(reel.id()).isEqualTo("1");
        assertThat(reel.reel()).isTrue();
        assertThat(reel.thumbnailUrl()).isEqualTo("https://cdn.test/1.jpg");
        assertThat(reel.permalink()).isEqualTo("https://instagram.com/reel/1");
        assertThat(reel.caption()).isEqualTo("New sarees");
        assertThat(reel.postedAt()).isEqualTo(Instant.parse("2026-11-01T10:15:30Z"));
        var post = media.get(1);
        assertThat(post.reel()).isFalse();
        assertThat(post.thumbnailUrl()).isNull();
        assertThat(post.caption()).isNull();
        assertThat(lastQuery.get())
                .startsWith("/17841/media?fields=")
                .contains("limit=50")
                .contains("access_token=secret+token");
    }

    @Test
    void readsAnAccountWithoutPosts() {
        respond(200, "{}");
        assertThat(client().recentMedia("17841", "token")).isEmpty();
    }

    @Test
    void downloadsACover() {
        status = 200;
        body = new byte[] {1, 2, 3};

        assertThat(client().download(baseUrl() + "/covers/1.jpg")).containsExactly(1, 2, 3);
    }

    @Test
    void reportsARefusalWithoutTheToken() {
        respond(400, "{\"error\": {\"message\": \"Invalid OAuth access token\"}}");

        assertThatThrownBy(() -> client().recentMedia("17841", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("/17841/media")
                .hasMessageNotContaining("secret");
    }

    @Test
    void reportsUnreachableInstagram() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var unreachable =
                new InstagramClient(new InstagramProperties("t", "a", "http://127.0.0.1:" + closedPort, null));

        assertThatThrownBy(() -> unreachable.recentMedia("a", "t"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not reach Instagram");
    }

    @Test
    void defaultsAndTrimsItsSettings() {
        var blank = new InstagramProperties(" ", "", " ", 0);
        assertThat(blank.configured()).isFalse();
        assertThat(blank.accessToken()).isNull();
        assertThat(blank.accountId()).isNull();
        assertThat(blank.baseUrl()).isEqualTo("https://graph.facebook.com/v23.0");
        assertThat(blank.limit()).isEqualTo(8);

        var set = new InstagramProperties(" token ", " 17841 ", null, 4);
        assertThat(set.configured()).isTrue();
        assertThat(set.accessToken()).isEqualTo("token");
        assertThat(set.accountId()).isEqualTo("17841");
        assertThat(set.limit()).isEqualTo(4);
    }
}

package com.vaabhi.storefront;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The Instagram API calls the reels sync needs: the account's recent media and a cover download. */
@Component
class InstagramClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Instagram sends {@code 2024-05-01T12:34:56+0000}. */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final InstagramProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    InstagramClient(InstagramProperties properties) {
        this.properties = properties;
    }

    /** One post from the account; {@code reel} is true for reels (Instagram's media_product_type REELS). */
    record Media(
            String id,
            boolean reel,
            @Nullable String thumbnailUrl,
            String permalink,
            @Nullable String caption,
            Instant postedAt) {}

    /** The account's latest posts, newest first (Graph API {@code GET /{ig-account-id}/media}). */
    List<Media> recentMedia(String accountId, String token) {
        var body = get("/" + encode(accountId)
                + "/media?fields=id,media_type,media_product_type,thumbnail_url,permalink,caption,timestamp"
                + "&limit=50&access_token=" + encode(token));
        List<Media> media = new ArrayList<>();
        for (JsonNode node : JSON.readTree(body).path("data")) {
            var id = text(node.path("id"));
            var permalink = text(node.path("permalink"));
            var timestamp = text(node.path("timestamp"));
            if (id == null || permalink == null || timestamp == null) {
                continue;
            }
            media.add(new Media(
                    id,
                    "REELS".equals(text(node.path("media_product_type"))),
                    text(node.path("thumbnail_url")),
                    permalink,
                    text(node.path("caption")),
                    OffsetDateTime.parse(timestamp, TIMESTAMP).toInstant()));
        }
        return media;
    }

    /** A reel's cover image (a JPEG on Instagram's CDN; those links expire, which is why covers are copied to R2). */
    byte[] download(String url) {
        return send(URI.create(url), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String get(String pathAndQuery) {
        return send(URI.create(properties.baseUrl() + pathAndQuery), HttpResponse.BodyHandlers.ofString());
    }

    private <T> T send(URI uri, HttpResponse.BodyHandler<T> handler) {
        var request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build();
        try {
            var response = http.send(request, handler);
            if (response.statusCode() != 200) {
                // Never log the URI: it carries the access token.
                throw new IllegalStateException(
                        "Instagram answered " + response.statusCode() + " for " + uri.getPath());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach Instagram", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Instagram", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static @Nullable String text(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return null;
        }
        var value = node.asString();
        return value.isBlank() ? null : value;
    }
}

package com.vaabhi.catalog;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Meta's Catalog Batch API: {@code POST /{catalog-id}/items_batch} with UPDATE (upsert) and DELETE requests. */
@Component
class MetaCatalogClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MetaCatalogProperties properties;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    MetaCatalogClient(MetaCatalogProperties properties) {
        this.properties = properties;
    }

    /** One request in a batch: {@code UPDATE} with the item's fields, or {@code DELETE} with just its id. */
    record Request(String method, Map<String, Object> data) {}

    /**
     * Sends one batch. Meta accepts it and processes the items in the background; per-item problems show up in
     * Commerce Manager (Catalogue → Issues), not here. Throws when Meta refuses the batch itself.
     */
    String send(List<Request> requests) {
        var form = "item_type=PRODUCT_ITEM&allow_upsert=true&requests="
                + encode(JSON.writeValueAsString(requests))
                + "&access_token=" + encode(Objects.requireNonNull(properties.accessToken()));
        var request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/"
                        + encode(Objects.requireNonNull(properties.catalogId())) + "/items_batch"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Meta refused the catalogue batch (" + response.statusCode() + "): " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach Meta", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to Meta", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

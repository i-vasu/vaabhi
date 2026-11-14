package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Meta catalogue that Instagram Shopping and the WhatsApp catalogue read from, for instant updates through the
 * Catalog API. Without both values nothing is pushed and Meta only picks changes up from the scheduled CSV feed.
 *
 * @param catalogId the catalogue's id (Commerce Manager → the catalogue → Settings)
 * @param accessToken a system-user token with {@code catalog_management} (Business settings → Users → System users
 *     → Generate token, with the catalogue assigned to that user); it does not expire
 */
@ConfigurationProperties("vaabhi.meta-catalog")
record MetaCatalogProperties(
        @Nullable String catalogId,
        @Nullable String accessToken,
        @Nullable String baseUrl) {

    MetaCatalogProperties {
        catalogId = catalogId == null || catalogId.isBlank() ? null : catalogId.trim();
        accessToken = accessToken == null || accessToken.isBlank() ? null : accessToken.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://graph.facebook.com/v23.0" : baseUrl;
    }

    boolean configured() {
        return catalogId != null && accessToken != null;
    }
}

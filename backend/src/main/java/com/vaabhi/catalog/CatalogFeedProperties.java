package com.vaabhi.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Meta (Instagram + WhatsApp) catalogue feed: the public addresses its rows link to.
 *
 * @param storefrontUrl the shop's website, for each product's link
 * @param mediaUrl the photo bucket's public domain, for each product's photos
 */
@ConfigurationProperties("vaabhi.catalog-feed")
record CatalogFeedProperties(String storefrontUrl, String mediaUrl) {

    CatalogFeedProperties {
        storefrontUrl =
                trimSlash(storefrontUrl == null || storefrontUrl.isBlank() ? "https://vaabhi.com" : storefrontUrl);
        mediaUrl = trimSlash(mediaUrl == null || mediaUrl.isBlank() ? "https://media.vaabhi.com" : mediaUrl);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

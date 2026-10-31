package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/**
 * A purchasable colour × size. {@code size} is null for one-size products such as sarees.
 *
 * @param stockLeft set only when stock is low (3 or fewer)
 */
public record VariantResponse(
        String sku,
        String colourName,
        String colourHex,
        @Nullable String size,
        int pricePaise,
        int mrpPaise,
        boolean inStock,
        @Nullable Integer stockLeft) {}

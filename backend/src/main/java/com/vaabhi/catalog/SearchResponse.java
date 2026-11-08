package com.vaabhi.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A page of products from every category: search results, or a whole-shop listing such as one occasion.
 *
 * @param query the search text as used (trimmed), or null for every product
 */
public record SearchResponse(
        @Nullable String query,
        List<ProductSummary> items,
        long total,
        int page,
        int pageSize,
        String sort,
        List<FilterGroup> filters,
        @Nullable PriceRange priceRange) {}

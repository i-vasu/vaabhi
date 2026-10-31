package com.vaabhi.catalog;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A storefront listing request. Values within one filter are OR-ed (silk or cotton); different filters are
 * AND-ed (silk and wedding). Colour, size and in-stock apply to the same variant.
 *
 * @param facets selected values by facet code, e.g. {@code fabric -> [silk, cotton]}
 * @param colours colour family codes
 * @param page 1-based page number
 */
public record ListingQuery(
        String categorySlug,
        Map<String, Set<String>> facets,
        Set<String> colours,
        Set<String> sizes,
        boolean inStock,
        @Nullable Integer priceMinPaise,
        @Nullable Integer priceMaxPaise,
        ProductSort sort,
        int page,
        int pageSize) {

    public static final int MAX_PAGE_SIZE = 48;

    public ListingQuery {
        facets = Map.copyOf(facets);
        colours = Set.copyOf(colours);
        sizes = Set.copyOf(sizes);
    }

    int offset() {
        return (page - 1) * pageSize;
    }
}

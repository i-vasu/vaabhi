package com.vaabhi.catalog;

import java.util.Arrays;
import java.util.Locale;

/** Listing sort orders, by their query-parameter value. */
public enum ProductSort {
    POPULAR("popular", "l.popularity DESC"),
    NEW("new", "l.published_at DESC"),
    PRICE_ASC("price_asc", "l.price_paise ASC"),
    PRICE_DESC("price_desc", "l.price_paise DESC"),
    DISCOUNT("discount", "(l.mrp_paise - l.price_paise)::numeric / l.mrp_paise DESC");

    private final String param;
    private final String orderBy;

    ProductSort(String param, String orderBy) {
        this.param = param;
        this.orderBy = orderBy;
    }

    public String param() {
        return param;
    }

    /** SQL ORDER BY over the listing CTE, with a stable tie-breaker so pages never overlap. */
    String orderBy() {
        return orderBy + ", l.id DESC";
    }

    static ProductSort fromParam(String value) {
        var normalised = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(sort -> sort.param.equals(normalised))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown sort '" + value + "'"));
    }
}

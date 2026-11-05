package com.vaabhi.catalog;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A product card in a listing. Prices are in rupees and come from the cheapest active variant.
 *
 * @param stockLeft set only when stock is low (3 or fewer pieces across variants)
 */
public record ProductSummary(
        String slug,
        String name,
        @Nullable String subtitle,
        @Nullable Badge badge,
        BigDecimal priceRupees,
        BigDecimal mrpRupees,
        int discountPercent,
        List<ColourSwatch> colours,
        boolean inStock,
        @Nullable Integer stockLeft,
        @Nullable MediaResponse image) {}

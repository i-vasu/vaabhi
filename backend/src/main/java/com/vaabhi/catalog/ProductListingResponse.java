package com.vaabhi.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ProductListingResponse(
        CategoryResponse category,
        List<ProductSummary> items,
        long total,
        int page,
        int pageSize,
        String sort,
        List<FilterGroup> filters,
        @Nullable PriceRange priceRange) {}

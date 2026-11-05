package com.vaabhi.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** A row in the admin product list. */
public record AdminProductSummary(
        long id,
        String slug,
        String name,
        CategoryRef category,
        ProductStatus status,
        int variantCount,
        long stockTotal,
        @Nullable BigDecimal minPriceRupees,
        OffsetDateTime updatedAt) {}

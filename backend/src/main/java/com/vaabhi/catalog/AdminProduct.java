package com.vaabhi.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A product as the admin sees it: every variant and add-on, active or not, with exact stock. */
public record AdminProduct(
        long id,
        String slug,
        String name,
        @Nullable String subtitle,
        @Nullable String description,
        String categorySlug,
        ProductStatus status,
        @Nullable Badge badge,
        @Nullable String careInstructions,
        @Nullable BigDecimal sareeLengthM,
        boolean blouseIncluded,
        @Nullable BigDecimal blouseLengthM,
        Map<String, List<String>> attributes,
        List<VariantForm> variants,
        List<AddonForm> addons,
        List<AdminMedia> media,
        @Nullable OffsetDateTime publishedAt,
        OffsetDateTime updatedAt) {}

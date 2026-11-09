package com.vaabhi.catalog;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The product page. Prices are in rupees; {@code priceRupees}/{@code mrpRupees} are the cheapest active variant's. */
public record ProductDetailResponse(
        String slug,
        String name,
        @Nullable String subtitle,
        @Nullable String description,
        @Nullable Badge badge,
        CategoryRef category,
        BigDecimal priceRupees,
        BigDecimal mrpRupees,
        int discountPercent,
        @Nullable String careInstructions,
        @Nullable BigDecimal sareeLengthM,
        boolean blouseIncluded,
        @Nullable BigDecimal blouseLengthM,
        @Nullable Integer gsm,
        @Nullable Integer threadCount,
        List<ProductDimension> dimensions,
        List<AttributeResponse> attributes,
        List<VariantResponse> variants,
        List<MediaResponse> media,
        List<AddonResponse> addons) {}

package com.vaabhi.catalog;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The product page. Prices are in paise; {@code pricePaise}/{@code mrpPaise} are the cheapest active variant's. */
public record ProductDetailResponse(
        String slug,
        String name,
        @Nullable String subtitle,
        @Nullable String description,
        @Nullable Badge badge,
        CategoryRef category,
        int pricePaise,
        int mrpPaise,
        int discountPercent,
        @Nullable String careInstructions,
        @Nullable BigDecimal sareeLengthM,
        boolean blouseIncluded,
        @Nullable BigDecimal blouseLengthM,
        List<AttributeResponse> attributes,
        List<VariantResponse> variants,
        List<MediaResponse> media,
        List<AddonResponse> addons) {}

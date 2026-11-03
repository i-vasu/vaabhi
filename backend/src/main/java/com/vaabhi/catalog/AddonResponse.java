package com.vaabhi.catalog;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** An optional extra, e.g. blouse stitching. {@code priceRupees} is added to the variant price (0 = included). */
public record AddonResponse(
        String code,
        AddonKind kind,
        String name,
        @Nullable String description,
        BigDecimal priceRupees,
        AddonInput input) {}

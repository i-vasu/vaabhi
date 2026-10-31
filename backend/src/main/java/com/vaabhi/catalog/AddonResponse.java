package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/** An optional extra, e.g. blouse stitching. {@code pricePaise} is added to the variant price (0 = included). */
public record AddonResponse(
        String code, AddonKind kind, String name, @Nullable String description, int pricePaise, AddonInput input) {}

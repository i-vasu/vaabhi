package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/** A category as the admin app sees it: the tax class its products are billed under (null = shop default). */
public record AdminCategory(long id, String name, @Nullable Long taxClassId) {}

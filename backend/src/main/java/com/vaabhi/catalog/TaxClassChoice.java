package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/** Body for picking a tax class; null clears the choice. */
public record TaxClassChoice(@Nullable Long taxClassId) {}

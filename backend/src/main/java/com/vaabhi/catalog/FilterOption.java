package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/**
 * One choice in a filter. {@code count} is how many products would match if it were added, given every other
 * filter currently applied (selections in the same group are not counted against each other).
 */
public record FilterOption(
        String code, String name, @Nullable String hex, long count, boolean selected) {}

package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

public record CategoryResponse(
        Long id, String slug, String name, @Nullable String description) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(), category.getSlug(), category.getName(), category.getDescription());
    }
}

package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;

/**
 * A storefront category.
 *
 * @param imageKey R2 key of its photo (renditions at {@code <key>/{400,800,1600}.webp}); null while it has none
 */
public record CategoryResponse(
        Long id,
        String slug,
        String name,
        @Nullable String description,
        @Nullable String imageKey) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getSlug(),
                category.getName(),
                category.getDescription(),
                category.getImageKey());
    }
}

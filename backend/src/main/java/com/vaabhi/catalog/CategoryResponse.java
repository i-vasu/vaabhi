package com.vaabhi.catalog;

public record CategoryResponse(Long id, String slug, String name) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getSlug(), category.getName());
    }
}

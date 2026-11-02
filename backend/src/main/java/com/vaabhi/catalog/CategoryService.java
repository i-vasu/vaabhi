package com.vaabhi.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryService {

    static final String CACHE = "categories";

    private final CategoryRepository repository;

    CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    @Cacheable(CACHE)
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return repository.findAllByOrderByPositionAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /** Sets or clears a category's photo; the caller has checked the upload. Clears the categories cache. */
    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public CategoryResponse setImage(long id, @Nullable String imageKey) {
        var category = repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        category.setImageKey(imageKey);
        return CategoryResponse.from(category);
    }
}

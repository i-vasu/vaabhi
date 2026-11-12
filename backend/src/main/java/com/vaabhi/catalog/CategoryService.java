package com.vaabhi.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryService {

    static final String CACHE = "categories";

    private final CategoryRepository repository;

    private final JdbcClient jdbc;

    CategoryService(CategoryRepository repository, JdbcClient jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AdminCategory> adminCategories() {
        return jdbc.sql("SELECT id, name, tax_class_id FROM category ORDER BY position, id")
                .query(AdminCategory.class)
                .list();
    }

    /** Sets or clears the tax class products in this category are billed under. */
    @Transactional
    public AdminCategory setTaxClass(long id, @Nullable Long taxClassId) {
        if (taxClassId != null
                && !jdbc.sql("SELECT EXISTS (SELECT 1 FROM tax_class WHERE id = :id)")
                        .param("id", taxClassId)
                        .query(Boolean.class)
                        .single()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown tax class");
        }
        int updated = jdbc.sql("UPDATE category SET tax_class_id = :taxClass WHERE id = :id")
                .param("taxClass", taxClassId)
                .param("id", id)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
        return jdbc.sql("SELECT id, name, tax_class_id FROM category WHERE id = :id")
                .param("id", id)
                .query(AdminCategory.class)
                .single();
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

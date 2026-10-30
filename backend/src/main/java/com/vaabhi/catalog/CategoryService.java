package com.vaabhi.catalog;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}

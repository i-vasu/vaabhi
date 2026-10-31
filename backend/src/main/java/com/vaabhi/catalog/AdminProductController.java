package com.vaabhi.catalog;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Catalogue management for the admin app. Requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
class AdminProductController {

    private final AdminProductService service;

    AdminProductController(AdminProductService service) {
        this.service = service;
    }

    @GetMapping("/catalog/options")
    CatalogOptions catalogOptions() {
        return service.options();
    }

    @GetMapping("/products")
    AdminProductPage listAdminProducts(
            @RequestParam(required = false) @Nullable ProductStatus status,
            @Parameter(description = "Category slug") @RequestParam(required = false) @Nullable String category,
            @Parameter(description = "Search name, URL or SKU") @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ≥ 1 and pageSize 1–100");
        }
        return service.list(status, category, q, page, pageSize);
    }

    @GetMapping("/products/{id}")
    AdminProduct getAdminProduct(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    AdminProduct createProduct(@Valid @RequestBody ProductForm form) {
        return service.create(form);
    }

    @PutMapping("/products/{id}")
    AdminProduct updateProduct(@PathVariable long id, @Valid @RequestBody ProductForm form) {
        return service.update(id, form);
    }

    @PostMapping("/products/{id}/publish")
    AdminProduct publishProduct(@PathVariable long id) {
        return service.changeStatus(id, ProductStatus.PUBLISHED);
    }

    @PostMapping("/products/{id}/unpublish")
    AdminProduct unpublishProduct(@PathVariable long id) {
        return service.changeStatus(id, ProductStatus.DRAFT);
    }

    @PostMapping("/products/{id}/archive")
    AdminProduct archiveProduct(@PathVariable long id) {
        return service.changeStatus(id, ProductStatus.ARCHIVED);
    }
}

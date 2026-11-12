package com.vaabhi.catalog;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Category tax classes for the admin app. Requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/categories")
class AdminCategoryController {

    private final CategoryService service;

    AdminCategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    List<AdminCategory> listAdminCategories() {
        return service.adminCategories();
    }

    @PutMapping("/{id}/tax-class")
    AdminCategory setTaxClass(@PathVariable long id, @Valid @RequestBody TaxClassChoice choice) {
        return service.setTaxClass(id, choice.taxClassId());
    }
}

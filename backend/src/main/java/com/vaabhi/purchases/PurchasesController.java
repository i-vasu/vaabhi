package com.vaabhi.purchases;

import jakarta.validation.Valid;
import java.util.List;
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

/** Suppliers and (later) their bills and payments. Requires the MONEY area (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/money/purchases")
class PurchasesController {

    private final SupplierService suppliers;

    PurchasesController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping("/suppliers")
    List<SupplierBalance> listSuppliers(@RequestParam(required = false) @Nullable String q) {
        return suppliers.list(q);
    }

    @GetMapping("/suppliers/{id}")
    SupplierStatement getSupplier(@PathVariable long id) {
        return suppliers.statement(id);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    SupplierCreated createSupplier(@Valid @RequestBody SupplierForm form) {
        return new SupplierCreated(suppliers.create(form));
    }

    @PutMapping("/suppliers/{id}")
    SupplierStatement updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierForm form) {
        suppliers.update(id, form);
        return suppliers.statement(id);
    }
}

package com.vaabhi.inventory;

import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a product was made from and who worked on it. Lives under the product (CATALOGUE permission); the stock
 * item's side lives under stock (STOCK permission).
 */
@RestController
class ProductMakingController {

    private final ProductMakingService making;

    ProductMakingController(ProductMakingService making) {
        this.making = making;
    }

    @GetMapping("/api/v1/admin/products/{productId}/making")
    ProductMaking getProductMaking(@PathVariable long productId) {
        return making.get(productId);
    }

    @PostMapping("/api/v1/admin/products/{productId}/making/materials")
    @ResponseStatus(HttpStatus.CREATED)
    ProductMaking addProductMaterial(
            @PathVariable long productId,
            @Valid @RequestBody ProductMaking.ProductMaterialForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return making.addMaterial(productId, form, jwt == null ? null : jwt.getClaimAsString("name"));
    }

    @DeleteMapping("/api/v1/admin/products/{productId}/making/materials/{materialId}")
    ProductMaking removeProductMaterial(@PathVariable long productId, @PathVariable long materialId) {
        return making.removeMaterial(productId, materialId);
    }

    @PostMapping("/api/v1/admin/products/{productId}/making/work")
    @ResponseStatus(HttpStatus.CREATED)
    ProductMaking addProductWork(@PathVariable long productId, @Valid @RequestBody ProductMaking.ProductWorkForm form) {
        return making.addWork(productId, form);
    }

    @DeleteMapping("/api/v1/admin/products/{productId}/making/work/{workId}")
    ProductMaking removeProductWork(@PathVariable long productId, @PathVariable long workId) {
        return making.removeWork(productId, workId);
    }

    @GetMapping("/api/v1/admin/stock/{id}/products")
    List<ProductMaking.StockUsedIn> getStockItemProducts(@PathVariable long id) {
        return making.usedIn(id);
    }
}

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Stock items, movements and the stock ledger. Needs the STOCK permission (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/stock")
class InventoryController {



    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    List<StockItem> listStock(
            @RequestParam(required = false) @Nullable String q, @RequestParam(defaultValue = "false") boolean low) {
        return inventory.list(q, low);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StockItem createStockItem(@Valid @RequestBody StockItem.StockItemForm form) {
        return inventory.save(null, form);
    }

    @GetMapping("/{id}")
    StockItem getStockItem(@PathVariable long id) {
        return inventory.get(id);
    }

    @PutMapping("/{id}")
    StockItem updateStockItem(@PathVariable long id, @Valid @RequestBody StockItem.StockItemForm form) {
        return inventory.save(id, form);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteStockItem(@PathVariable long id) {
        inventory.remove(id);
    }

    @GetMapping("/{id}/movements")
    List<StockMovement> getStockLedger(@PathVariable long id) {
        return inventory.ledger(id);
    }

    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    StockMovement moveStock(
            @PathVariable long id,
            @Valid @RequestBody StockMovement.StockMovementForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return inventory.move(id, form, jwt == null ? null : jwt.getClaimAsString("name"));
    }
}

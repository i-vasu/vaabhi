package com.vaabhi.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Something the shop keeps in stock.
 *
 * @param onHand what is in stock now (sum of all movements)
 * @param low on hand is at or below the reorder level
 * @param valuePaise on hand × cost
 */
public record StockItem(
        long id,
        String name,
        @Nullable String sku,
        @Nullable String category,
        Unit unit,
        BigDecimal reorderLevel,
        long costPaise,
        BigDecimal onHand,
        boolean low,
        long valuePaise) {

    public enum Unit {
        PCS,
        M,
        CM,
        KG,
        G,
        L,
        ROLL,
        BOX,
        SET
    }

    /** Add or edit a stock item. */
    public record StockItemForm(
            @NotBlank @Size(max = 120) String name,
            @Nullable @Size(max = 40) String sku,
            @Nullable @Size(max = 40) String category,
            @NotNull Unit unit,
            @NotNull @DecimalMin("0") BigDecimal reorderLevel,
            @Min(0) @Max(100_000_000) long costPaise) {}
}

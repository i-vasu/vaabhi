package com.vaabhi.inventory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
 * @param valueRupees on hand × cost
 */
public record StockItem(
        long id,
        String name,
        @Nullable String sku,
        @Nullable String category,
        Unit unit,
        BigDecimal reorderLevel,
        BigDecimal costRupees,
        BigDecimal onHand,
        boolean low,
        BigDecimal valueRupees) {

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

            @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
            BigDecimal costRupees) {}
}

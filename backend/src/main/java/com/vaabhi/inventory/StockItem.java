package com.vaabhi.inventory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Something the shop keeps in stock.
 *
 * @param onHand what is in stock now (sum of all movements)
 * @param low on hand is at or below the reorder level
 * @param valueRupees on hand × cost
 * @param supplierName the shop it was bought from
 * @param gsm fabric weight in grams per square metre
 * @param widthIn fabric width in inches
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
        BigDecimal valueRupees,
        @Nullable String supplierName,
        @Nullable String supplierPhone,
        @Nullable String material,
        @Nullable String colour,
        @Nullable Integer gsm,
        @Nullable Integer threadCount,
        @Nullable BigDecimal widthIn,
        @Nullable String notes,
        List<StockPhoto> photos) {

    /** A reference photo of the lot; the 400 px WebP lives at {@code <key>/400.webp}. */
    public record StockPhoto(long id, String key) {}

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
            BigDecimal costRupees,

            @Nullable @Size(max = 120) String supplierName,

            @Nullable @Pattern(regexp = "^\\+?[0-9 -]{6,20}$", message = "Enter a phone number")
            String supplierPhone,

            @Nullable @Size(max = 60) String material,
            @Nullable @Size(max = 60) String colour,
            @Nullable @Min(1) @Max(2000) Integer gsm,
            @Nullable @Min(1) @Max(5000) Integer threadCount,

            @Nullable @DecimalMin("1") @DecimalMax("999") @Digits(integer = 3, fraction = 1)
            BigDecimal widthIn,

            @Nullable @Size(max = 500) String notes) {}
}

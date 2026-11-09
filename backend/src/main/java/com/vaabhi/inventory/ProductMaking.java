package com.vaabhi.inventory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * How a product was made: the stock it was cut from and the work done on it, with what each cost.
 *
 * @param costRupees materials plus work
 */
public record ProductMaking(List<ProductMaterial> materials, List<ProductWork> work, BigDecimal costRupees) {

    /**
     * Stock used for the product, e.g. 2.5 m of a 10 m fabric lot.
     *
     * @param costRupees quantity × the item's unit cost
     */
    public record ProductMaterial(
            long id,
            long stockItemId,
            String stockItemName,
            StockItem.Unit unit,
            BigDecimal quantity,
            BigDecimal costRupees,
            @Nullable String supplierName,
            @Nullable String material,
            @Nullable Integer gsm,
            @Nullable Integer threadCount,
            @Nullable String note,
            OffsetDateTime createdAt) {}

    /** Stitching, embroidery or other work someone did on the product. */
    public record ProductWork(
            long id,
            @Nullable Long staffId,
            String workerName,
            String workType,
            BigDecimal costRupees,
            @Nullable LocalDate doneOn,
            @Nullable String note) {}

    /** Takes {@code quantity} of a stock item out of stock for the product. */
    public record ProductMaterialForm(
            @Positive long stockItemId,

            @NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer = 6, fraction = 2)
            BigDecimal quantity,

            @Nullable @Size(max = 200) String note) {}

    /** Records work on the product; {@code staffId} when a staff member did it, else just the name. */
    public record ProductWorkForm(
            @Nullable Long staffId,
            @NotBlank @Size(max = 120) String workerName,
            @NotBlank @Size(max = 60) String workType,

            @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
            BigDecimal costRupees,

            @Nullable LocalDate doneOn,
            @Nullable @Size(max = 200) String note) {}

    /** A product made from a stock item, for the item's page. */
    public record StockUsedIn(
            long productId,
            String productName,
            String productSlug,
            String productStatus,
            BigDecimal quantity,
            OffsetDateTime createdAt) {}
}

package com.vaabhi.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Current prices and availability for a bag. Totals count only lines that can be bought, at the quantity that can
 * be bought ({@code min(requested, maxQuantity)}). Shipping is added at checkout.
 */
public record BagQuote(
        List<Line> lines,
        BigDecimal itemsRupees,
        BigDecimal addonsRupees,
        BigDecimal mrpRupees,
        BigDecimal totalRupees) {

    @Schema(name = "BagLineStatus")
    public enum Status {
        /** In stock for the requested quantity. */
        AVAILABLE,
        /** Fewer left than requested; {@code maxQuantity} says how many. */
        LIMITED,
        /** The variant exists but none are left. */
        OUT_OF_STOCK,
        /** Unpublished, removed or the add-on was withdrawn. */
        UNAVAILABLE
    }

    @Schema(name = "BagQuoteLine")
    public record Line(
            String sku,
            @Nullable String addonCode,
            Status status,
            int quantity,
            int maxQuantity,
            @Nullable Integer stockLeft,
            @Nullable String slug,
            @Nullable String name,
            @Nullable String colourName,
            @Nullable String colourHex,
            @Nullable String size,
            BigDecimal priceRupees,
            BigDecimal mrpRupees,
            @Nullable String addonName,
            BigDecimal addonPriceRupees,
            @Nullable AddonInput addonInput,
            @Nullable String imageKey) {}
}

package com.vaabhi.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Current prices and availability for a bag. Totals count only lines that can be bought, at the quantity that can
 * be bought ({@code min(requested, maxQuantity)}). Shipping is added at checkout.
 */
public record BagQuote(List<Line> lines, int itemsPaise, int addonsPaise, int mrpPaise, int totalPaise) {

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
            int pricePaise,
            int mrpPaise,
            @Nullable String addonName,
            int addonPricePaise,
            @Nullable AddonInput addonInput,
            @Nullable String imageKey) {}
}

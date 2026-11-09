package com.vaabhi.tailoring;

import com.vaabhi.inventory.StockItem;
import com.vaabhi.settings.ProductionStage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A piece cut from stock on its way to becoming a storefront product, as the workshop sees it. Carries everything
 * the WhatsApp share message needs.
 *
 * @param productStatus DRAFT while on the board
 * @param quantity how much of the stock went into it
 * @param photoKey the stock item's first reference photo
 * @param assignedPhone the assignee's phone, for sharing on WhatsApp
 * @param photos design references for the tailor
 */
public record WorkPiece(
        long pieceId,
        long productId,
        String productName,
        String productSlug,
        String productStatus,
        long stockItemId,
        String stockItemName,
        BigDecimal quantity,
        StockItem.Unit unit,
        @Nullable String photoKey,
        ProductionStage stage,
        OffsetDateTime stageSince,
        @Nullable Assignee assignedTo,
        @Nullable String assignedPhone,
        @Nullable String note,
        List<Photo> photos,
        Fabric fabric,
        List<Dimension> dimensions) {

    /** A design reference photo; the 400 px WebP lives at {@code <key>/400.webp}. */
    public record Photo(long id, String key) {}

    /** The stock's fabric details. */
    public record Fabric(
            @Nullable String material,
            @Nullable String colour,
            @Nullable BigDecimal widthIn,
            @Nullable Integer gsm,
            @Nullable Integer threadCount) {}

    /** A product measurement, e.g. Length 44 IN. */
    public record Dimension(String label, BigDecimal value, String unit) {}
}

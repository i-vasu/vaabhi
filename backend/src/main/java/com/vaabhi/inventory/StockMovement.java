package com.vaabhi.inventory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A change to what is on hand.
 *
 * @param quantity signed: IN adds, OUT takes away, ADJUST either way
 * @param balance on hand after this movement
 * @param orderNumber the order it was used for, if any
 */
public record StockMovement(
        long id,
        long itemId,
        String itemName,
        Kind kind,
        BigDecimal quantity,
        BigDecimal balance,
        @Nullable Long orderId,
        @Nullable String orderNumber,
        @Nullable String note,
        @Nullable String movedBy,
        OffsetDateTime movedAt) {

    public enum Kind {
        IN,
        OUT,
        ADJUST
    }

    /**
     * Record a movement. For IN and OUT give the amount moved (positive); for ADJUST the signed correction.
     *
     * @param orderId the order cloth or material went into
     */
    public record StockMovementForm(
            @NotNull Kind kind,

            @NotNull @DecimalMin("-1000000") @DecimalMax("1000000")
            BigDecimal quantity,

            @Nullable Long orderId,
            @Nullable @Size(max = 200) String note) {}
}

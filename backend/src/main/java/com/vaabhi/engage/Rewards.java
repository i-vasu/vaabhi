package com.vaabhi.engage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Loyalty points records. */
public final class Rewards {

    private Rewards() {}

    /**
     * How points work.
     *
     * @param pointsPer100 earned for every ₹100 of a delivered order
     * @param paisePerPoint what one point is worth when redeemed
     */
    public record RewardSettings(
            boolean enabled,
            @Min(0) @Max(100) int pointsPer100,
            @Min(0) @Max(10_000) int paisePerPoint) {}

    /** One change to a customer's points. */
    public record RewardEntry(
            long id,
            Kind kind,
            int points,
            @Nullable Long orderId,
            @Nullable String orderNumber,
            @Nullable String note,
            @Nullable String createdBy,
            OffsetDateTime createdAt) {}

    public enum Kind {
        EARNED,
        REDEEMED,
        ADJUSTED,
        /** Points redeemed on an order that was then cancelled, given back. */
        RETURNED
    }

    /**
     * A customer's points.
     *
     * @param valuePaise what the balance is worth when redeemed
     */
    public record RewardAccount(long clientId, int balance, long valuePaise, List<RewardEntry> entries) {}

    /**
     * Correct the balance (signed) by hand; points are redeemed when booking an order.
     *
     * @param kind ADJUSTED (REDEEMED is refused)
     */
    public record RewardChange(
            @NotNull Kind kind,
            @Min(-100_000) @Max(100_000) int points,
            @Nullable Long orderId,
            @Nullable @Size(max = 200) String note) {}
}

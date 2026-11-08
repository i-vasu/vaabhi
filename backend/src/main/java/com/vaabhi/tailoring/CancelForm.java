package com.vaabhi.tailoring;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Cancel an order. When money was paid on it, say what happens to that money.
 *
 * @param settlement REFUND gives everything paid back now (in {@code refundMode}); KEEP keeps it, e.g. as a
 *     cancellation charge. Required when anything was paid.
 */
public record CancelForm(
        @Nullable Settlement settlement,
        @Nullable PaymentMode refundMode,
        @Nullable @Size(max = 200) String reason) {

    public enum Settlement {
        REFUND,
        KEEP
    }
}

package com.vaabhi.tailoring;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** Money received for an order (positive) or refunded (negative). */
public record OrderPayment(
        long id,
        long amountPaise,
        PaymentMode mode,
        Kind kind,
        @Nullable String note,
        @Nullable String receivedBy,
        OffsetDateTime receivedAt) {

    public enum Kind {
        ADVANCE,
        PAYMENT,
        REFUND
    }
}

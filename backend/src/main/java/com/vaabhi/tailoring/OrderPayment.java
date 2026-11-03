package com.vaabhi.tailoring;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** Money received for an order (positive) or refunded (negative). */
public record OrderPayment(
        long id,
        BigDecimal amountRupees,
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

package com.vaabhi.checkout;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Something happened to an online order that the customer hears about by WhatsApp and email (after commit). */
record WebOrderEvent(
        Type type,
        String number,
        String phone,
        BigDecimal totalRupees,
        WebOrder.PaymentMethod paymentMethod,
        @Nullable String courier,
        @Nullable String trackingNumber,
        @Nullable BigDecimal refundedRupees) {

    enum Type {
        PLACED,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }
}

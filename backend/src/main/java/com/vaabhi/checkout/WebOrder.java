package com.vaabhi.checkout;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** An online order with the prices it was placed at. */
public record WebOrder(
        String number,
        Status status,
        String contactName,
        String contactPhone,
        @Nullable String contactEmail,
        String addressLine1,
        @Nullable String addressLine2,
        String city,
        String state,
        String pincode,
        BigDecimal itemsRupees,
        BigDecimal shippingRupees,
        BigDecimal totalRupees,
        @Nullable String razorpayPaymentId,
        @Nullable OffsetDateTime paidAt,
        OffsetDateTime createdAt,
        List<Line> lines) {

    public enum Status {
        PENDING_PAYMENT,
        PAID,
        FAILED
    }

    public record Line(
            String sku,
            String productSlug,
            String name,
            @Nullable String colourName,
            @Nullable String size,
            @Nullable String addonName,
            @Nullable String blouseSize,
            int quantity,
            BigDecimal unitRupees,
            BigDecimal addonRupees,
            BigDecimal amountRupees,
            @Nullable String imageKey) {}
}

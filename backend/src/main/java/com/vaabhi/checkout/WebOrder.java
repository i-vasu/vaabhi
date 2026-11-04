package com.vaabhi.checkout;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An online order with the prices it was placed at.
 *
 * @param paidAt when the money came in: at payment for ONLINE, on delivery for COD
 * @param refundedRupees what was refunded through Razorpay when a paid order was cancelled
 * @param refundStatus where that refund is (Razorpay's refund webhooks move it on)
 * @param trackingStatus the courier's latest status, when shipped through Shiprocket
 * @param trackingUrl the public tracking page, when shipped through Shiprocket
 * @param shippingLabelUrl the label to print (PDF), when shipped through Shiprocket
 */
public record WebOrder(
        String number,
        Status status,
        PaymentMethod paymentMethod,
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
        @Nullable String courier,
        @Nullable String trackingNumber,
        @Nullable OffsetDateTime shippedAt,
        @Nullable OffsetDateTime deliveredAt,
        @Nullable OffsetDateTime cancelledAt,
        @Nullable String cancelReason,
        @Nullable BigDecimal refundedRupees,
        @Nullable String refundId,
        @Nullable RefundStatus refundStatus,
        @Nullable String trackingStatus,
        @Nullable String trackingUrl,
        @Nullable String shippingLabelUrl,
        OffsetDateTime createdAt,
        List<Line> lines) {

    /** PENDING: Razorpay accepted the refund; PROCESSED: the money reached the customer; FAILED: it did not. */
    public enum RefundStatus {
        PENDING,
        PROCESSED,
        FAILED
    }

    /** PENDING_PAYMENT → PLACED → SHIPPED → DELIVERED; CANCELLED from PLACED or SHIPPED; FAILED payments. */
    public enum Status {
        PENDING_PAYMENT,
        FAILED,
        PLACED,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }

    /** ONLINE: paid with Razorpay at checkout. COD: cash collected on delivery. */
    public enum PaymentMethod {
        ONLINE,
        COD
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

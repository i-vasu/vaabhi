package com.vaabhi.checkout;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An online order with the prices it was placed at.
 *
 * @param couponCode the coupon applied at checkout (upper case), when there was one
 * @param discountRupees what the coupon took off the items (zero when there was none)
 * @param paidAt when the money came in: at payment for ONLINE, on delivery for COD
 * @param refundedRupees what is refunded through Razorpay when a paid order was cancelled
 * @param refundId Razorpay's refund id; null until Razorpay has accepted the refund
 * @param refundStatus where that refund is (Razorpay's refund webhooks move it on)
 * @param refundError why Razorpay refused the refund, when it did (the owner can send it again)
 * @param trackingStatus the courier's latest status, when shipped through Shiprocket
 * @param trackingUrl the public tracking page, when shipped through Shiprocket
 * @param shippingLabelUrl the label to print (PDF), when shipped through Shiprocket
 * @param returnableUntil until when the customer can ask to return items (delivered orders only)
 * @param returns the return requests on this order, oldest first
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
        @Nullable String couponCode,
        BigDecimal discountRupees,
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
        @Nullable String refundError,
        @Nullable String trackingStatus,
        @Nullable String trackingUrl,
        @Nullable String shippingLabelUrl,
        @Nullable OffsetDateTime returnableUntil,
        OffsetDateTime createdAt,
        List<Line> lines,
        List<OrderReturn> returns) {

    /**
     * PENDING: owed and sent or about to be sent (no refund id until Razorpay accepts it); PROCESSED: the money
     * reached the customer; FAILED: Razorpay refused it or it failed on the way (the owner can send it again).
     */
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

    /** @param position the line's place in the order, used to pick lines for a return */
    public record Line(
            int position,
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

    /** REQUESTED → APPROVED → REFUNDED, or REJECTED. */
    public enum ReturnStatus {
        REQUESTED,
        APPROVED,
        REJECTED,
        REFUNDED
    }

    /** RAZORPAY: part of the online payment refunded. MANUAL: paid back by the shop (UPI or bank), reference kept. */
    public enum RefundMethod {
        RAZORPAY,
        MANUAL
    }

    /**
     * Some of the order's items coming back.
     *
     * @param itemsRupees what the returned items cost at the prices paid
     * @param refundRupees what was refunded (may be less than {@code itemsRupees}, e.g. a damaged item)
     * @param refundReference the Razorpay refund id, or the shop's UPI or bank reference
     * @param pickupAwb the courier's tracking number for the return pickup, when booked through Shiprocket
     */
    public record OrderReturn(
            String number,
            ReturnStatus status,
            String reason,
            @Nullable String shopNote,
            BigDecimal itemsRupees,
            @Nullable BigDecimal refundRupees,
            @Nullable RefundMethod refundMethod,
            @Nullable String refundReference,
            @Nullable RefundStatus refundStatus,
            @Nullable String pickupCourier,
            @Nullable String pickupAwb,
            @Nullable String pickupStatus,
            OffsetDateTime requestedAt,
            @Nullable OffsetDateTime refundedAt,
            List<ReturnLine> lines) {}

    public record ReturnLine(int position, String name, int quantity) {}
}

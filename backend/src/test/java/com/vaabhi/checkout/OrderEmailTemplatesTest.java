package com.vaabhi.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the order and return emails say. */
class OrderEmailTemplatesTest {

    private static final String SITE = "https://vaabhi.com";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-04T10:00:00+05:30");

    private static WebOrder.Line line() {
        return new WebOrder.Line(
                0,
                "KAN-1",
                "kanjeevaram",
                "Kanjeevaram <silk> saree",
                "Rani pink",
                null,
                "Stitched blouse",
                null,
                2,
                new BigDecimal("9250.00"),
                new BigDecimal("0.00"),
                new BigDecimal("18500.00"),
                null);
    }

    private static WebOrder order(
            WebOrder.Status status,
            WebOrder.PaymentMethod method,
            String courier,
            String tracking,
            String trackingUrl,
            BigDecimal refunded,
            List<WebOrder.OrderReturn> returns) {
        return order(status, method, courier, tracking, trackingUrl, refunded, returns, null, BigDecimal.ZERO);
    }

    private static WebOrder order(
            WebOrder.Status status,
            WebOrder.PaymentMethod method,
            String courier,
            String tracking,
            String trackingUrl,
            BigDecimal refunded,
            List<WebOrder.OrderReturn> returns,
            String couponCode,
            BigDecimal discountRupees) {
        return new WebOrder(
                "WEB-1001",
                status,
                method,
                "Meera Iyer",
                "+919876501234",
                "meera@example.com",
                "4 Lake View Road",
                "Near the park",
                "New Delhi",
                "Delhi",
                "110001",
                new BigDecimal("18500.00"),
                new BigDecimal("0.00"),
                couponCode,
                discountRupees,
                new BigDecimal("18500.50"),
                null,
                null,
                courier,
                tracking,
                null,
                null,
                null,
                refunded == null ? null : "Out of stock",
                refunded,
                null,
                null,
                null,
                null,
                trackingUrl,
                null,
                status == WebOrder.Status.DELIVERED ? NOW.plusDays(7) : null,
                NOW,
                List.of(line()),
                returns,
                null);
    }

    private static WebOrder.OrderReturn returned(
            WebOrder.ReturnStatus status, String note, String awb, BigDecimal refund, WebOrder.RefundMethod method) {
        return new WebOrder.OrderReturn(
                "WEB-1001-R1",
                status,
                "Too long",
                note,
                new BigDecimal("9250.00"),
                refund,
                method,
                method == WebOrder.RefundMethod.MANUAL ? "UPI-1" : null,
                null,
                awb == null ? null : "Delhivery",
                awb,
                null,
                NOW,
                null,
                List.of(new WebOrder.ReturnLine(0, "Kanjeevaram silk saree", 1)));
    }

    @Test
    void placedListsTheItemsTotalAndAddressAndEscapesShopText() {
        var cod = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.PLACED,
                order(WebOrder.Status.PLACED, WebOrder.PaymentMethod.COD, null, null, null, null, List.of()),
                SITE);
        assertThat(cod.to()).isEqualTo("meera@example.com");
        assertThat(cod.subject()).isEqualTo("Your Vaabhi order WEB-1001 is placed");
        assertThat(cod.text())
                .startsWith("Hi Meera,")
                .contains("keep ₹18,500.50 ready to pay when it is delivered")
                .contains("- 2 × Kanjeevaram <silk> saree (Rani pink, Stitched blouse): ₹18,500")
                .contains("Shipping: Free")
                .contains("Delivering to: Meera Iyer, 4 Lake View Road, Near the park, New Delhi, Delhi 110001")
                .contains("Your orders: https://vaabhi.com/account/orders");
        assertThat(cod.html())
                .contains("Kanjeevaram &lt;silk&gt; saree")
                .doesNotContain("<silk>")
                .contains("href=\"https://vaabhi.com/account/orders\"");

        var online = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.PLACED,
                order(WebOrder.Status.PLACED, WebOrder.PaymentMethod.ONLINE, null, null, null, null, List.of()),
                SITE);
        assertThat(online.text()).contains("We have received your payment of ₹18,500.50.");
    }

    @Test
    void couponDiscountListsTheCodeAndAmount() {
        var discounted = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.PLACED,
                order(
                        WebOrder.Status.PLACED,
                        WebOrder.PaymentMethod.ONLINE,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        "SAVE10",
                        new BigDecimal("1850.00")),
                SITE);
        assertThat(discounted.text()).contains("Coupon SAVE10: -₹1,850");
        assertThat(discounted.html()).contains("Coupon SAVE10").contains("-₹1,850");

        var none = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.PLACED,
                order(WebOrder.Status.PLACED, WebOrder.PaymentMethod.ONLINE, null, null, null, null, List.of()),
                SITE);
        assertThat(none.text()).doesNotContain("Coupon");
        assertThat(none.html()).doesNotContain("Coupon");
    }

    @Test
    void shippedGivesTheCourierTrackingAndLink() {
        var shipped = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.SHIPPED,
                order(
                        WebOrder.Status.SHIPPED,
                        WebOrder.PaymentMethod.COD,
                        "Delhivery",
                        "AWB505",
                        "https://shiprocket.co/tracking/AWB505",
                        null,
                        List.of()),
                SITE);
        assertThat(shipped.subject()).isEqualTo("Your Vaabhi order WEB-1001 has shipped");
        assertThat(shipped.text())
                .contains("on its way with Delhivery, tracking number AWB505.")
                .contains("Track your parcel: https://shiprocket.co/tracking/AWB505")
                .contains("ready to pay on delivery");
        assertThat(shipped.html()).contains("href=\"https://shiprocket.co/tracking/AWB505\"");

        var byHand = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.SHIPPED,
                order(WebOrder.Status.SHIPPED, WebOrder.PaymentMethod.ONLINE, null, null, null, null, List.of()),
                SITE);
        assertThat(byHand.text())
                .contains("on its way with our delivery partner.")
                .doesNotContain("Track");
    }

    @Test
    void deliveredAndCancelledSayWhatHappensNext() {
        var delivered = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.DELIVERED,
                order(WebOrder.Status.DELIVERED, WebOrder.PaymentMethod.ONLINE, null, null, null, null, List.of()),
                SITE);
        assertThat(delivered.text()).contains("has been delivered").contains("until 2026-11-11");

        var cancelled = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.CANCELLED,
                order(
                        WebOrder.Status.CANCELLED,
                        WebOrder.PaymentMethod.ONLINE,
                        null,
                        null,
                        null,
                        new BigDecimal("18500.50"),
                        List.of()),
                SITE);
        assertThat(cancelled.subject()).isEqualTo("Your Vaabhi order WEB-1001 is cancelled");
        assertThat(cancelled.text())
                .contains("has been cancelled (Out of stock).")
                .contains("₹18,500.50 is being refunded to your original payment method");

        var codCancelled = OrderEmailTemplates.forOrder(
                WebOrderEvent.Type.CANCELLED,
                order(WebOrder.Status.CANCELLED, WebOrder.PaymentMethod.COD, null, null, null, null, List.of()),
                SITE);
        assertThat(codCancelled.text()).contains("has been cancelled.").doesNotContain("refunded");
    }

    @Test
    void returnStepsExplainThemselves() {
        var approved = OrderEmailTemplates.forReturn(
                WebOrderReturnEvent.Type.APPROVED,
                order(
                        WebOrder.Status.DELIVERED,
                        WebOrder.PaymentMethod.ONLINE,
                        null,
                        null,
                        null,
                        null,
                        List.of(returned(WebOrder.ReturnStatus.APPROVED, "Pickup tomorrow", null, null, null))),
                "WEB-1001-R1",
                SITE);
        assertThat(approved).isNotNull();
        assertThat(approved.subject()).isEqualTo("Your return WEB-1001-R1 is approved");
        assertThat(approved.text())
                .contains("approved your return of 1 × Kanjeevaram silk saree.")
                .contains("Note from Vaabhi: Pickup tomorrow");

        var pickup = OrderEmailTemplates.forReturn(
                WebOrderReturnEvent.Type.PICKUP_BOOKED,
                order(
                        WebOrder.Status.DELIVERED,
                        WebOrder.PaymentMethod.ONLINE,
                        null,
                        null,
                        null,
                        null,
                        List.of(returned(WebOrder.ReturnStatus.APPROVED, null, "AWB9", null, null))),
                "WEB-1001-R1",
                SITE);
        assertThat(pickup.text())
                .contains("A courier (Delhivery) will collect 1 × Kanjeevaram silk saree from you,"
                        + " pickup number AWB9.");

        var rejected = OrderEmailTemplates.forReturn(
                WebOrderReturnEvent.Type.REJECTED,
                order(
                        WebOrder.Status.DELIVERED,
                        WebOrder.PaymentMethod.ONLINE,
                        null,
                        null,
                        null,
                        null,
                        List.of(returned(WebOrder.ReturnStatus.REJECTED, "Worn", null, null, null))),
                "WEB-1001-R1",
                SITE);
        assertThat(rejected.subject()).isEqualTo("About your return WEB-1001-R1");
        assertThat(rejected.text()).contains("could not accept").contains("Note from Vaabhi: Worn");

        var refunded = OrderEmailTemplates.forReturn(
                WebOrderReturnEvent.Type.REFUNDED,
                order(
                        WebOrder.Status.DELIVERED,
                        WebOrder.PaymentMethod.ONLINE,
                        null,
                        null,
                        null,
                        null,
                        List.of(returned(
                                WebOrder.ReturnStatus.REFUNDED,
                                null,
                                null,
                                new BigDecimal("9150.00"),
                                WebOrder.RefundMethod.RAZORPAY))),
                "WEB-1001-R1",
                SITE);
        assertThat(refunded.text()).contains("₹9,150 is being refunded to your original payment method");

        var byHand = OrderEmailTemplates.forReturn(
                WebOrderReturnEvent.Type.REFUNDED,
                order(
                        WebOrder.Status.DELIVERED,
                        WebOrder.PaymentMethod.COD,
                        null,
                        null,
                        null,
                        null,
                        List.of(returned(
                                WebOrder.ReturnStatus.REFUNDED,
                                null,
                                null,
                                new BigDecimal("9150.00"),
                                WebOrder.RefundMethod.MANUAL))),
                "WEB-1001-R1",
                SITE);
        assertThat(byHand.text()).contains("₹9,150 has been sent to you (reference UPI-1).");

        assertThat(OrderEmailTemplates.forReturn(
                        WebOrderReturnEvent.Type.APPROVED,
                        order(WebOrder.Status.DELIVERED, WebOrder.PaymentMethod.COD, null, null, null, null, List.of()),
                        "WEB-1001-R9",
                        SITE))
                .as("an unknown return gets no email")
                .isNull();
    }

    @Test
    void formatsRupeesTheIndianWay() {
        assertThat(OrderEmailTemplates.rupees(new BigDecimal("125000"))).isEqualTo("₹1,25,000");
        assertThat(OrderEmailTemplates.rupees(new BigDecimal("99.5"))).isEqualTo("₹99.50");
    }
}

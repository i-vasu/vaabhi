package com.vaabhi.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.email.EmailMessage;
import com.vaabhi.shared.email.EmailSender;
import com.vaabhi.shared.whatsapp.WhatsAppSender;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The storefront order flows end to end against fake Razorpay and Shiprocket servers: pay online, cash on delivery,
 * ship with Shiprocket, tracking and delivery webhooks, cancel (shop and customer), pay again, the payment check,
 * returns with partial refunds, and Razorpay's refund webhooks.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, CheckoutIT.Mail.class})
class CheckoutIT {

    /** Keeps the emails instead of sending them. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Mail {
        @Bean
        @Primary
        CapturingEmail capturingEmail() {
            return new CapturingEmail();
        }

        @Bean
        @Primary
        CapturingWhatsApp capturingWhatsApp() {
            return new CapturingWhatsApp();
        }
    }

    /** Keeps WhatsApp messages; set up like the defaults: only "placed" and "delivered" have approved templates. */
    static class CapturingWhatsApp implements WhatsAppSender {
        record Sent(String phone, String template, java.util.Map<String, String> variables) {}

        final List<Sent> sent = new CopyOnWriteArrayList<>();

        @Override
        public boolean configured(String template) {
            return template.equals("web_order_placed") || template.equals("web_order_delivered");
        }

        @Override
        public void send(String phone, String template, java.util.Map<String, String> variables) {
            sent.add(new Sent(phone, template, variables));
        }

        List<Sent> about(String order) {
            return sent.stream()
                    .filter(s -> order.equals(s.variables().get("order")))
                    .toList();
        }
    }

    static class CapturingEmail implements EmailSender {
        final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public void send(EmailMessage message) {
            sent.add(message);
        }

        List<EmailMessage> to(String address) {
            return sent.stream().filter(m -> m.to().equals(address)).toList();
        }
    }

    private static final String KEY_SECRET = "rzp-test-secret";
    private static final String WEBHOOK_SECRET = "rzp-webhook-secret";
    private static final String SHIPROCKET_TOKEN = "sr-webhook-token";
    private static final FakeGateways GATEWAYS = start();
    private static final AtomicInteger PHONES = new AtomicInteger(10_000);

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Demo Owner"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    private static FakeGateways start() {
        try {
            return new FakeGateways();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void gateways(DynamicPropertyRegistry registry) {
        registry.add("vaabhi.razorpay.key-id", () -> "rzp_test_key");
        registry.add("vaabhi.razorpay.key-secret", () -> KEY_SECRET);
        registry.add("vaabhi.razorpay.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("vaabhi.razorpay.base-url", GATEWAYS::url);
        registry.add("vaabhi.shiprocket.email", () -> "api@vaabhi.test");
        registry.add("vaabhi.shiprocket.password", () -> "secret");
        registry.add("vaabhi.shiprocket.pickup-pincode", () -> "560001");
        registry.add("vaabhi.shiprocket.webhook-token", () -> SHIPROCKET_TOKEN);
        registry.add("vaabhi.shiprocket.base-url", GATEWAYS::url);
    }

    @AfterAll
    static void stop() {
        GATEWAYS.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PaymentReconciler reconciler;

    private String sku;
    private RequestPostProcessor customer;
    private String customerEmail;

    @Autowired
    private CapturingEmail emails;

    @Autowired
    private CapturingWhatsApp whatsApp;

    /** A published, active variant with 10 in stock, and a signed-in customer of our own. */
    @BeforeEach
    void setUp() {
        GATEWAYS.refundsList = null;
        GATEWAYS.refuseRefunds = false;
        sku = jdbc.sql("""
                        SELECT v.sku FROM product_variant v JOIN product p ON p.id = v.product_id
                        WHERE p.status = 'PUBLISHED' AND v.active ORDER BY v.id LIMIT 1
                        """).query(String.class).single();
        jdbc.sql("UPDATE product_variant SET stock_qty = 10 WHERE sku = :sku")
                .param("sku", sku)
                .update();
        long customerId = jdbc.sql("""
                        INSERT INTO customer (name, email, phone) VALUES ('Meera', :email, :phone) RETURNING id
                        """)
                .param("email", customerEmail = "meera" + PHONES.incrementAndGet() + "@vaabhi.test")
                .param("phone", "+9197" + PHONES.get() + "123")
                .query(Long.class)
                .single();
        customer = jwt().jwt(j -> j.subject(Long.toString(customerId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    // --- the flows ----------------------------------------------------------------------------------------------

    @Test
    void paidOnlineShippedWithShiprocketDeliveredThenPartlyReturnedAndRefunded() throws Exception {
        jdbc.sql("""
                        UPDATE product SET shipping_weight_grams = 750
                        WHERE id = (SELECT product_id FROM product_variant WHERE sku = :sku)
                        """).param("sku", sku).update();
        var started = checkout("ONLINE", 2, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        String razorpayOrderId = read(started, "$.razorpayOrderId");
        assertThat((String) read(started, "$.razorpayKeyId")).isEqualTo("rzp_test_key");
        assertThat(GATEWAYS.last("POST", "/v1/orders").json().path("amount").asLong())
                .isEqualTo(((Number) read(started, "$.amountPaise")).longValue());
        assertThat(stock()).isEqualTo(10);

        // Razorpay Checkout returns a signed payment: the order is placed and stock taken.
        pay(number, razorpayOrderId, "pay_A1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.razorpayPaymentId").value("pay_A1"));
        assertThat(stock()).isEqualTo(8);

        // Ship with Shiprocket: order, AWB, pickup, label; 2 × 750 g.
        admin("/" + number + "/shiprocket", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.courier").value("Delhivery Surface"))
                .andExpect(jsonPath("$.trackingNumber").value(org.hamcrest.Matchers.startsWith("AWB")))
                .andExpect(jsonPath("$.trackingUrl").value(org.hamcrest.Matchers.startsWith("https://shiprocket.co/")))
                .andExpect(
                        jsonPath("$.shippingLabelUrl").value(org.hamcrest.Matchers.startsWith("https://labels.test/")));
        var created = GATEWAYS.last("POST", "/v1/external/orders/create/adhoc").json();
        assertThat(created.path("order_id").asString()).isEqualTo(number);
        assertThat(created.path("payment_method").asString()).isEqualTo("Prepaid");
        assertThat(created.path("weight").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(created.path("billing_phone").asString()).hasSize(10);
        assertThat(GATEWAYS.last("POST", "/v1/external/courier/generate/pickup")
                        .headers()
                        .get("authorization"))
                .isEqualTo("Bearer sr-token");
        String awb = shopOrder(number, "trackingNumber");

        // Tracking: a manual refresh, then Shiprocket's webhook reports delivery.
        admin("/" + number + "/tracking", "")
                .andExpect(jsonPath("$.trackingStatus").value("IN TRANSIT"));
        shiprocketWebhook("{\"awb\":\"" + awb + "\",\"current_status\":\"DELIVERED\"}", "wrong")
                .andExpect(status().isUnauthorized());
        shiprocketWebhook("{\"awb\":\"" + awb + "\",\"current_status\":\"DELIVERED\"}", SHIPROCKET_TOKEN)
                .andExpect(status().isOk());
        myOrder(number)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.returnableUntil").isNotEmpty());

        // The customer returns one of the two; the shop approves, books the pickup and refunds part of it.
        customerPost("/orders/" + number + "/returns", """
                        {"reason":"Colour differs from the photo","items":[{"position":0,"quantity":3}]}
                        """).andExpect(status().isConflict());
        var requested = customerPost("/orders/" + number + "/returns", """
                        {"reason":"Colour differs from the photo","items":[{"position":0,"quantity":1}]}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returns[0].status").value("REQUESTED"));
        String returnNumber = read(requested, "$.returns[0].number");
        // The shop's Orders list can show just the orders with a return to handle.
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("openReturns", "true")
                        .param("q", number)
                        .with(OWNER))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].openReturn").value(true));
        BigDecimal unit = decimal(read(requested, "$.lines[0].unitRupees"));
        assertThat(decimal(read(requested, "$.returns[0].itemsRupees"))).isEqualByComparingTo(unit);

        admin("/returns/" + returnNumber + "/approve", "{\"note\":\"Pickup tomorrow\"}")
                .andExpect(jsonPath("$.returns[0].status").value("APPROVED"));
        admin("/returns/" + returnNumber + "/pickup", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returns[0].pickupAwb").value(org.hamcrest.Matchers.startsWith("AWB")));
        var pickup = GATEWAYS.last("POST", "/v1/external/orders/create/return").json();
        assertThat(pickup.path("order_id").asString()).isEqualTo(returnNumber);
        assertThat(pickup.path("shipping_pincode").asString()).isEqualTo("560001");
        assertThat(pickup.path("order_items").get(0).path("units").asInt()).isEqualTo(1);
        assertThat(GATEWAYS.last("POST", "/v1/external/courier/assign/awb")
                        .json()
                        .path("is_return")
                        .asInt())
                .isEqualTo(1);

        var partial = unit.subtract(new BigDecimal("100.00"));
        admin("/returns/" + returnNumber + "/refund", "{\"amountRupees\":" + partial + ",\"reference\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returns[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.returns[0].refundMethod").value("RAZORPAY"))
                .andExpect(jsonPath("$.returns[0].refundStatus").value("PENDING"));
        var refund = GATEWAYS.last("POST", "/v1/payments/pay_A1/refund").json();
        assertThat(refund.path("amount").asLong()).isEqualTo(RazorpayClient.paise(partial));
        assertThat(stock()).isEqualTo(9);
        String refundId = shopOrder(number, "returns[0].refundReference");

        // Razorpay reports the refund reached the customer.
        razorpayWebhook("{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"" + refundId
                        + "\",\"payment_id\":\"pay_A1\",\"status\":\"processed\"}}}}")
                .andExpect(status().isOk());
        myOrder(number).andExpect(jsonPath("$.returns[0].refundStatus").value("PROCESSED"));

        // Nothing more than what was paid can be refunded.
        var second = customerPost("/orders/" + number + "/returns", """
                        {"reason":"Second one too","items":[{"position":0,"quantity":1}]}
                        """).andExpect(status().isCreated());
        String secondReturn = read(second, "$.returns[1].number");
        admin("/returns/" + secondReturn + "/refund", "{\"amountRupees\":99999999,\"reference\":null}")
                .andExpect(status().isConflict());
    }

    @Test
    void cashOnDeliveryIsPlacedAtOnceAndTheCustomerCanCancelIt() throws Exception {
        var started = checkout("COD", 1, "110001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.razorpayOrderId").isEmpty());
        String number = read(started, "$.orderNumber");
        assertThat(stock()).isEqualTo(9);
        assertThat(GATEWAYS.last("GET", "/v1/external/courier/serviceability").query())
                .contains("cod=1");

        customerPost("/orders/" + number + "/cancel", "{\"reason\":\"Ordered by mistake\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Cancelled by the customer: Ordered by mistake"))
                .andExpect(jsonPath("$.refundedRupees").isEmpty());
        assertThat(stock()).isEqualTo(10);
        customerPost("/orders/" + number + "/cancel", "{\"reason\":null}").andExpect(status().isConflict());
    }

    @Test
    void codDeliveredAndReturnedIsRefundedByHandWithAReference() throws Exception {
        String number = read(checkout("COD", 1, "110001").andExpect(status().isCreated()), "$.orderNumber");
        admin("/" + number + "/ship", "{\"courier\":\"Local\",\"trackingNumber\":null}")
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        admin("/" + number + "/deliver", "")
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        String returnNumber = read(
                customerPost("/orders/" + number + "/returns", """
                                {"reason":"Too long","items":[{"position":0,"quantity":1}]}
                                """).andExpect(status().isCreated()),
                "$.returns[0].number");
        admin("/returns/" + returnNumber + "/refund", "{\"amountRupees\":500,\"reference\":null}")
                .andExpect(status().isBadRequest());
        admin("/returns/" + returnNumber + "/refund", "{\"amountRupees\":500,\"reference\":\"UPI-4471\"}")
                .andExpect(jsonPath("$.returns[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.returns[0].refundMethod").value("MANUAL"))
                .andExpect(jsonPath("$.returns[0].refundReference").value("UPI-4471"));
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void anUnpaidOrderCanBePaidAgainAndThePaymentCheckPlacesIt() throws Exception {
        var started = checkout("ONLINE", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        String razorpayOrderId = read(started, "$.razorpayOrderId");
        // Razorpay reports a failed attempt.
        razorpayWebhook(paymentEvent("payment.failed", razorpayOrderId, "pay_F1"))
                .andExpect(status().isOk());
        myOrder(number).andExpect(jsonPath("$.status").value("FAILED"));

        customerPost("/orders/" + number + "/pay", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razorpayOrderId").value(razorpayOrderId))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        // The browser never came back, but the payment was authorized: the check captures it and places the order.
        jdbc.sql("UPDATE web_order SET payment_started_at = now() - interval '20 minutes' WHERE number = :n")
                .param("n", number)
                .update();
        GATEWAYS.paymentsByOrder.put(
                razorpayOrderId,
                "{\"id\":\"pay_F1\",\"status\":\"failed\",\"amount\":1},{\"id\":\"pay_A2\",\"status\":\"authorized\","
                        + "\"amount\":" + ((Number) read(started, "$.amountPaise")).longValue() + "}");
        reconciler.reconcile();
        assertThat(GATEWAYS.calls("POST", "/v1/payments/pay_A2/capture")).hasSize(1);
        myOrder(number)
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.razorpayPaymentId").value("pay_A2"));
        assertThat(stock()).isEqualTo(9);
        customerPost("/orders/" + number + "/pay", "").andExpect(status().isConflict());
    }

    @Test
    void aWaitingOrderIsGivenUpAfterTwoHoursAndThePaymentWebhookStillPlacesIt() throws Exception {
        var started = checkout("ONLINE", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        String razorpayOrderId = read(started, "$.razorpayOrderId");
        jdbc.sql("UPDATE web_order SET payment_started_at = now() - interval '3 hours' WHERE number = :n")
                .param("n", number)
                .update();
        reconciler.reconcile();
        myOrder(number).andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(stock()).isEqualTo(10);

        razorpayWebhook(paymentEvent("payment.captured", razorpayOrderId, "pay_W1"))
                .andExpect(status().isOk());
        razorpayWebhook(paymentEvent("payment.captured", razorpayOrderId, "pay_W1"))
                .andExpect(status().isOk());
        myOrder(number).andExpect(jsonPath("$.status").value("PLACED"));
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void cancellingAShippedOrderCancelsItInShiprocketAndRefundsInFull() throws Exception {
        var started = checkout("ONLINE", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        pay(number, read(started, "$.razorpayOrderId"), "pay_C1").andExpect(status().isOk());
        admin("/" + number + "/shiprocket", "").andExpect(jsonPath("$.status").value("SHIPPED"));
        customerPost("/orders/" + number + "/cancel", "{\"reason\":null}").andExpect(status().isConflict());

        admin("/" + number + "/cancel", "{\"reason\":\"Customer called\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.trackingStatus").value("Cancelled in Shiprocket"))
                .andExpect(jsonPath("$.refundStatus").value("PENDING"));
        assertThat(GATEWAYS.last("POST", "/v1/external/orders/cancel")
                        .json()
                        .path("ids")
                        .get(0)
                        .asLong())
                .isPositive();
        var refund = GATEWAYS.last("POST", "/v1/payments/pay_C1/refund").json();
        assertThat(refund.path("amount").asLong()).isEqualTo(((Number) read(started, "$.amountPaise")).longValue());
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void razorpayRefusingTheRefundLeavesItFailedAndTheOwnerCanSendItAgain() throws Exception {
        var started = checkout("ONLINE", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        pay(number, read(started, "$.razorpayOrderId"), "pay_R1").andExpect(status().isOk());

        GATEWAYS.refuseRefunds = true;
        admin("/" + number + "/cancel", "{\"reason\":\"Customer called\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundStatus").value("FAILED"))
                .andExpect(
                        jsonPath("$.refundError").value(org.hamcrest.Matchers.containsString("The amount is invalid")));
        assertThat(stock()).isEqualTo(10);

        GATEWAYS.refuseRefunds = false;
        var retried = admin("/" + number + "/refund", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("PENDING"))
                .andExpect(jsonPath("$.refundId").isNotEmpty());
        assertThat((String) read(retried, "$.refundError")).isNull();
    }

    @Test
    void retryingARefundThatIsNotOwedIsRefused() throws Exception {
        var started = checkout("ONLINE", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        pay(number, read(started, "$.razorpayOrderId"), "pay_R2").andExpect(status().isOk());

        // A PLACED order has no refund to send yet.
        admin("/" + number + "/refund", "").andExpect(status().isConflict());

        // Cancelling refunds it, so the refund is already PENDING: sending it again is refused.
        admin("/" + number + "/cancel", "{\"reason\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("PENDING"));
        admin("/" + number + "/refund", "").andExpect(status().isConflict());
    }

    @Test
    void retryReusesARefundRazorpayAlreadyMadeInsteadOfRefundingAgain() throws Exception {
        var started = checkout("ONLINE", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        pay(number, read(started, "$.razorpayOrderId"), "pay_R3").andExpect(status().isOk());
        long paise = ((Number) read(started, "$.amountPaise")).longValue();

        // The first refund is refused: the order is cancelled with the refund FAILED.
        GATEWAYS.refuseRefunds = true;
        admin("/" + number + "/cancel", "{\"reason\":\"Customer called\"}")
                .andExpect(jsonPath("$.refundStatus").value("FAILED"));

        // Razorpay did make a refund after all (a timed-out earlier try): the retry reuses it.
        GATEWAYS.refuseRefunds = false;
        GATEWAYS.refundsList = "[{\"id\":\"rfnd_OLD\",\"amount\":" + paise + ",\"status\":\"processed\"}]";
        int refundPostsBefore =
                GATEWAYS.calls("POST", "/v1/payments/pay_R3/refund").size();
        admin("/" + number + "/refund", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("rfnd_OLD"))
                .andExpect(jsonPath("$.refundStatus").value("PROCESSED"));
        assertThat(GATEWAYS.calls("POST", "/v1/payments/pay_R3/refund")).hasSize(refundPostsBefore);
    }

    @Test
    void cancellingACashOnDeliveryOrderMakesNoRazorpayRefund() throws Exception {
        var started = checkout("COD", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        int paymentPostsBefore = GATEWAYS.calls("POST", "/v1/payments/").size();
        int paymentGetsBefore = GATEWAYS.calls("GET", "/v1/payments/").size();

        admin("/" + number + "/cancel", "{\"reason\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundStatus").isEmpty())
                .andExpect(jsonPath("$.refundId").isEmpty());
        assertThat(GATEWAYS.calls("POST", "/v1/payments/")).hasSize(paymentPostsBefore);
        assertThat(GATEWAYS.calls("GET", "/v1/payments/")).hasSize(paymentGetsBefore);
    }

    @Test
    void aCustomerCancelsTheirOwnPaidOrderAndIsRefunded() throws Exception {
        var started = checkout("ONLINE", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        pay(number, read(started, "$.razorpayOrderId"), "pay_R4").andExpect(status().isOk());

        customerPost("/orders/" + number + "/cancel", "{\"reason\":\"Changed my mind\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundStatus").value("PENDING"));
    }

    @Test
    void refusesUnservedPincodesForgedPaymentsAndForgedWebhooks() throws Exception {
        checkout("ONLINE", 1, "999999")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("We cannot deliver to pincode 999999 yet."));

        var started = checkout("ONLINE", 1, "110001").andExpect(status().isCreated());
        String number = read(started, "$.orderNumber");
        customerPost("/orders/" + number + "/payment", """
                        {"razorpayOrderId":"%s","razorpayPaymentId":"pay_X","razorpaySignature":"forged"}
                        """.formatted((String) read(started, "$.razorpayOrderId")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "forged")
                        .content(paymentEvent("payment.captured", read(started, "$.razorpayOrderId"), "pay_X")))
                .andExpect(status().isBadRequest());
        myOrder(number).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void websiteOrdersShowInTheShopsOrdersListAndCounterStaffShipButCannotRefund() throws Exception {
        String number = read(checkout("COD", 1, "110001").andExpect(status().isCreated()), "$.orderNumber");
        // Never-paid online orders stay out of the list.
        String unpaid = read(checkout("ONLINE", 1, "110001").andExpect(status().isCreated()), "$.orderNumber");

        var both = mockMvc.perform(
                        get("/api/v1/admin/orders").param("pageSize", "100").with(OWNER))
                .andExpect(status().isOk());
        assertThat((java.util.List<String>) read(both, "$.items[*].source"))
                .isNotEmpty()
                .allMatch(source -> source.equals("SHOP") || source.equals("ONLINE"));
        java.util.List<Object> mine = read(both, "$.items[?(@.number == '" + number + "')]");
        assertThat(mine).hasSize(1);
        assertThat((java.util.List<String>) read(both, "$.items[?(@.number == '" + unpaid + "')].number"))
                .isEmpty();
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("source", "ONLINE")
                        .param("filter", "OPEN")
                        .param("q", number.toLowerCase(java.util.Locale.ROOT))
                        .with(OWNER))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].source").value("ONLINE"))
                .andExpect(jsonPath("$.items[0].onlineStatus").value("PLACED"))
                .andExpect(jsonPath("$.items[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.items[0].clientName").value("Meera Iyer"))
                .andExpect(jsonPath("$.items[0].clientId").isEmpty())
                .andExpect(jsonPath("$.items[0].deliveryDate").isEmpty())
                .andExpect(jsonPath("$.items[0].balanceRupees").isNotEmpty());
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("source", "SHOP")
                        .param("q", number)
                        .with(OWNER))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/v1/admin/me/notifications").with(OWNER))
                .andExpect(
                        jsonPath("$.alerts[?(@.kind == 'TO_SHIP')].href").value("/orders/?source=ONLINE&filter=OPEN"));

        // Counter staff (Orders permission) open and ship it, but cancelling and refunds are the owner's.
        var counter = jwt().jwt(j -> j.subject("staff:1").claim("name", "Counter"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("PERM_ORDERS"));
        mockMvc.perform(get("/api/v1/admin/online-orders/" + number).with(counter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(number));
        mockMvc.perform(post("/api/v1/admin/online-orders/" + number + "/cancel")
                        .with(counter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":null}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/online-orders/returns/" + number + "-R1/refund")
                        .with(counter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountRupees\":1,\"reference\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/online-orders/" + number + "/ship")
                        .with(counter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courier\":\"Local\",\"trackingNumber\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("source", "ONLINE")
                        .param("q", number)
                        .with(counter))
                .andExpect(jsonPath("$.items[0].status").value("READY"))
                .andExpect(jsonPath("$.items[0].onlineStatus").value("SHIPPED"));
        admin("/" + number + "/cancel", "{\"reason\":\"Owner cancels\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void emailsTheCustomerWhenTheOrderIsPlacedShippedAndCancelledButOnlyWithAnAddress() throws Exception {
        String number = read(checkout("COD", 1, "110001").andExpect(status().isCreated()), "$.orderNumber");
        admin("/" + number + "/ship", "{\"courier\":\"Local\",\"trackingNumber\":\"T-9\"}")
                .andExpect(status().isOk());
        admin("/" + number + "/cancel", "{\"reason\":null}").andExpect(status().isOk());

        // WhatsApp: only the messages with an approved template ("placed"), to the delivery phone.
        assertThat(whatsApp.about(number)).singleElement().satisfies(sent -> {
            assertThat(sent.template()).isEqualTo("web_order_placed");
            assertThat(sent.phone()).isEqualTo("+919876501234");
            assertThat(sent.variables())
                    .containsEntry("payment", "Cash on delivery")
                    .hasEntrySatisfying("amount", amount -> assertThat(amount).matches("[\\d,]+"));
        });

        var mine = emails.to(customerEmail);
        assertThat(mine)
                .extracting(EmailMessage::subject)
                .containsExactly(
                        "Your Vaabhi order " + number + " is placed",
                        "Your Vaabhi order " + number + " has shipped",
                        "Your Vaabhi order " + number + " is cancelled");
        assertThat(mine.get(0).text())
                .contains("Delivering to: Meera Iyer, 4 Lake View, Delhi")
                .contains("Total: ₹");
        assertThat(mine.get(0).html()).contains("/account/orders");
        assertThat(mine.get(1).text()).contains("on its way with Local, tracking number T-9.");

        // A customer who signed in with a phone only has no email address: nothing is sent.
        long phoneOnly = jdbc.sql("INSERT INTO customer (name, phone) VALUES ('Ravi', :phone) RETURNING id")
                .param("phone", "+9196" + PHONES.incrementAndGet() + "123")
                .query(Long.class)
                .single();
        customer = jwt().jwt(j -> j.subject(Long.toString(phoneOnly)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        int before = emails.sent.size();
        checkout("COD", 1, "110001").andExpect(status().isCreated());
        assertThat(emails.sent).hasSize(before);
    }

    @Test
    void emailsEachStepOfAReturn() throws Exception {
        String number = read(checkout("COD", 1, "110001").andExpect(status().isCreated()), "$.orderNumber");
        admin("/" + number + "/deliver", "").andExpect(status().isOk());
        String returnNumber = read(
                customerPost("/orders/" + number + "/returns", """
                                {"reason":"Too long","items":[{"position":0,"quantity":1}]}
                                """).andExpect(status().isCreated()),
                "$.returns[0].number");
        admin("/returns/" + returnNumber + "/approve", "{\"note\":\"Pickup tomorrow\"}")
                .andExpect(status().isOk());
        admin("/returns/" + returnNumber + "/pickup", "").andExpect(status().isOk());
        admin("/returns/" + returnNumber + "/refund", "{\"amountRupees\":100,\"reference\":\"UPI-7\"}")
                .andExpect(status().isOk());

        assertThat(emails.to(customerEmail))
                .extracting(EmailMessage::subject)
                .contains(
                        "Your Vaabhi order " + number + " is delivered",
                        "Your return " + returnNumber + " is approved",
                        "Pickup booked for your return " + returnNumber,
                        "Your refund for return " + returnNumber);
        assertThat(emails.to(customerEmail).getLast().text()).contains("₹100 has been sent to you (reference UPI-7).");
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private ResultActions checkout(String method, int quantity, String pincode) throws Exception {
        return customerPost("/checkout", """
                        {"lines":[{"sku":"%s","addonCode":null,"blouseSize":null,"quantity":%d}],
                         "address":{"name":"Meera Iyer","phone":"9876501234","line1":"4 Lake View","line2":null,
                                    "city":"Delhi","state":"Delhi","pincode":"%s"},
                         "paymentMethod":"%s"}
                        """.formatted(sku, quantity, pincode, method));
    }

    private ResultActions pay(String number, String razorpayOrderId, String paymentId) throws Exception {
        var signature = RazorpayClient.hmac(KEY_SECRET, razorpayOrderId + "|" + paymentId);
        return customerPost("/orders/" + number + "/payment", """
                        {"razorpayOrderId":"%s","razorpayPaymentId":"%s","razorpaySignature":"%s"}
                        """.formatted(razorpayOrderId, paymentId, signature));
    }

    private static String paymentEvent(String event, String razorpayOrderId, String paymentId) {
        return "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"order_id\":\"" + razorpayOrderId + "\"}}}}";
    }

    private ResultActions razorpayWebhook(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", RazorpayClient.hmac(WEBHOOK_SECRET, body))
                .content(body));
    }

    private ResultActions shiprocketWebhook(String body, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/webhooks/delivery-updates")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", token)
                .content(body));
    }

    private ResultActions customerPost(String path, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/customer" + path)
                .with(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions myOrder(String number) throws Exception {
        return mockMvc.perform(get("/api/v1/customer/orders/" + number).with(customer))
                .andExpect(status().isOk());
    }

    private ResultActions admin(String path, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/online-orders" + path)
                .with(OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** A field of the order as the shop sees it (from the online orders list). */
    private String shopOrder(String number, String field) throws Exception {
        var list = mockMvc.perform(get("/api/v1/admin/online-orders").with(OWNER))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        java.util.List<String> values = JsonPath.read(list, "$[?(@.number == '" + number + "')]." + field);
        assertThat(values).hasSize(1);
        return values.getFirst();
    }

    private int stock() {
        return jdbc.sql("SELECT stock_qty FROM product_variant WHERE sku = :sku")
                .param("sku", sku)
                .query(Integer.class)
                .single();
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    private static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private static BigDecimal decimal(Object number) {
        return new BigDecimal(number.toString());
    }
}

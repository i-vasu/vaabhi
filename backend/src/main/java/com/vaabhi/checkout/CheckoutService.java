package com.vaabhi.checkout;

import com.vaabhi.catalog.BagQuote;
import com.vaabhi.catalog.BagQuoteRequest;
import com.vaabhi.catalog.BagQuoteService;
import com.vaabhi.catalog.CatalogStock;
import com.vaabhi.customer.CustomerService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Places online orders from the bag and records their Razorpay payments. */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NUMBER = "number";
    private static final String CUSTOMER = "customer";

    private final JdbcClient jdbc;
    private final BagQuoteService quotes;
    private final CatalogStock stock;
    private final CustomerService customers;
    private final RazorpayClient razorpay;
    private final CheckoutProperties properties;

    CheckoutService(
            JdbcClient jdbc,
            BagQuoteService quotes,
            CatalogStock stock,
            CustomerService customers,
            RazorpayClient razorpay,
            CheckoutProperties properties) {
        this.jdbc = jdbc;
        this.quotes = quotes;
        this.stock = stock;
        this.customers = customers;
        this.razorpay = razorpay;
        this.properties = properties;
    }

    /**
     * Prices the bag again on the server, saves the order (waiting for payment) and creates its Razorpay order.
     * Fails with 409 when an item is no longer available in the quantity asked for.
     */
    @Transactional
    public CheckoutStart start(long customerId, CheckoutRequest request) {
        var keyId = razorpay.keyId();
        var profile = customers.profile(customerId);
        var quote = quotes.quote(new BagQuoteRequest(request.lines().stream()
                .map(l -> new BagQuoteRequest.Line(l.sku(), l.addonCode(), l.quantity()))
                .toList()));
        for (var line : quote.lines()) {
            if (line.status() != BagQuote.Status.AVAILABLE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Some items in your bag have changed (" + (line.name() == null ? line.sku() : line.name())
                                + "). Please review your bag.");
            }
        }
        var items = quote.totalRupees();
        var shipping = items.compareTo(properties.freeShippingFromRupees()) >= 0
                ? Rupees.ZERO
                : Rupees.of(properties.shippingRupees());
        var total = items.add(shipping);
        var number = "WEB-"
                + jdbc.sql("SELECT nextval('web_order_number_seq')")
                        .query(Long.class)
                        .single();
        var address = request.address();
        var phone = "+91" + address.phone();
        long orderId = jdbc.sql("""
                        INSERT INTO web_order (number, customer_id, contact_name, contact_phone, contact_email,
                            address_line1, address_line2, city, state, pincode, items_rupees, shipping_rupees,
                            total_rupees)
                        VALUES (:number, :customer, :name, :phone, :email, :line1, :line2, :city, :state, :pincode,
                            :items, :shipping, :total)
                        RETURNING id
                        """)
                .param(NUMBER, number)
                .param(CUSTOMER, customerId)
                .param("name", address.name().trim())
                .param("phone", phone)
                .param("email", profile.email())
                .param("line1", address.line1().trim())
                .param("line2", blankToNull(address.line2()))
                .param("city", address.city().trim())
                .param("state", address.state().trim())
                .param("pincode", address.pincode())
                .param("items", items)
                .param("shipping", shipping)
                .param("total", total)
                .query(Long.class)
                .single();
        for (var i = 0; i < quote.lines().size(); i++) {
            var line = quote.lines().get(i);
            var unit = line.priceRupees();
            var addon = line.addonPriceRupees();
            jdbc.sql("""
                            INSERT INTO web_order_line (order_id, position, sku, product_slug, name, colour_name, size,
                                addon_code, addon_name, blouse_size, quantity, unit_rupees, addon_rupees, amount_rupees,
                                image_key)
                            VALUES (:order, :position, :sku, :slug, :name, :colour, :size, :addonCode, :addonName,
                                :blouseSize, :quantity, :unit, :addon, :amount, :image)
                            """)
                    .param("order", orderId)
                    .param("position", i)
                    .param("sku", line.sku())
                    .param("slug", line.slug())
                    .param("name", line.name())
                    .param("colour", line.colourName())
                    .param("size", line.size())
                    .param("addonCode", line.addonCode())
                    .param("addonName", line.addonName())
                    .param("blouseSize", blankToNull(request.lines().get(i).blouseSize()))
                    .param("quantity", line.quantity())
                    .param("unit", unit)
                    .param("addon", addon)
                    .param("amount", Rupees.times(unit.add(addon), line.quantity()))
                    .param("image", line.imageKey())
                    .update();
        }
        var razorpayOrderId = razorpay.createOrder(total, number);
        jdbc.sql("UPDATE web_order SET razorpay_order_id = :rzp WHERE id = :id")
                .param("rzp", razorpayOrderId)
                .param("id", orderId)
                .update();
        return new CheckoutStart(
                number,
                total,
                razorpayOrderId,
                keyId,
                RazorpayClient.paise(total),
                "INR",
                address.name().trim(),
                phone,
                profile.email());
    }

    /** Records the payment Razorpay Checkout returned to the browser, once its signature checks out. */
    @Transactional
    public WebOrder confirm(long customerId, String number, PaymentConfirmation payment) {
        var order = order(customerId, number);
        if (order.status() == WebOrder.Status.PAID) {
            return order;
        }
        var expected = razorpayOrderId(number);
        if (!payment.razorpayOrderId().equals(expected)
                || !razorpay.paymentSignatureValid(
                        payment.razorpayOrderId(), payment.razorpayPaymentId(), payment.razorpaySignature())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The payment could not be verified");
        }
        markPaid(payment.razorpayOrderId(), payment.razorpayPaymentId());
        return order(customerId, number);
    }

    /**
     * Razorpay's webhook: payment.captured / order.paid mark the order paid (in case the browser never came back),
     * payment.failed marks a waiting order failed. Unknown events and orders are ignored.
     */
    @Transactional
    public void webhook(String body, String signature) {
        if (!razorpay.webhookSignatureValid(body, signature)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
        }
        var event = JSON.readTree(body);
        var payment = event.path("payload").path("payment").path("entity");
        var orderId = text(payment, "order_id");
        var paymentId = text(payment, "id");
        if (orderId == null || paymentId == null) {
            return;
        }
        switch (event.path("event").asString("")) {
            case "payment.captured", "order.paid" -> markPaid(orderId, paymentId);
            case "payment.failed" ->
                jdbc.sql("UPDATE web_order SET status = 'FAILED' WHERE razorpay_order_id = :rzp"
                                + " AND status = 'PENDING_PAYMENT'")
                        .param("rzp", orderId)
                        .update();
            default ->
                log.debug("Ignoring Razorpay event {}", event.path("event").asString(""));
        }
    }

    /** Marks the order paid once (a repeat from the webhook or the browser does nothing) and takes its stock. */
    private void markPaid(String razorpayOrderId, String razorpayPaymentId) {
        var updated = jdbc.sql("""
                        UPDATE web_order SET status = 'PAID', razorpay_payment_id = :payment, paid_at = now()
                        WHERE razorpay_order_id = :rzp AND status <> 'PAID'
                        RETURNING id
                        """)
                .param("payment", razorpayPaymentId)
                .param("rzp", razorpayOrderId)
                .query(Long.class)
                .optional();
        updated.ifPresent(id -> {
            Map<String, Integer> sold = new LinkedHashMap<>();
            jdbc.sql("SELECT sku, quantity FROM web_order_line WHERE order_id = :id")
                    .param("id", id)
                    .query((rs, n) -> Map.entry(rs.getString("sku"), rs.getInt("quantity")))
                    .list()
                    .forEach(e -> sold.merge(e.getKey(), e.getValue(), Integer::sum));
            stock.take(sold);
        });
    }

    @Transactional(readOnly = true)
    public List<WebOrder> orders(long customerId) {
        return jdbc
                .sql(SELECT + " WHERE o.customer_id = :customer ORDER BY o.created_at DESC LIMIT 50")
                .param(CUSTOMER, customerId)
                .query(OrderRow.class)
                .list()
                .stream()
                .map(this::withLines)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebOrder order(long customerId, String number) {
        return find(
                        " WHERE o.number = :number AND o.customer_id = :customer",
                        Map.of(NUMBER, number, CUSTOMER, customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    /** The shop's view: paid orders and those still waiting, newest first. */
    @Transactional(readOnly = true)
    public List<WebOrder> recent(int limit) {
        return jdbc
                .sql(SELECT + " ORDER BY o.created_at DESC LIMIT :limit")
                .param("limit", limit)
                .query(OrderRow.class)
                .list()
                .stream()
                .map(this::withLines)
                .toList();
    }

    private static final String SELECT = """
            SELECT o.id, o.number, o.status, o.contact_name, o.contact_phone, o.contact_email, o.address_line1,
                   o.address_line2, o.city, o.state, o.pincode, o.items_rupees, o.shipping_rupees, o.total_rupees,
                   o.razorpay_payment_id, o.paid_at, o.created_at
            FROM web_order o
            """;

    private record OrderRow(
            long id,
            String number,
            WebOrder.Status status,
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
            OffsetDateTime createdAt) {}

    private Optional<WebOrder> find(String where, Map<String, Object> params) {
        return jdbc.sql(SELECT + where)
                .params(params)
                .query(OrderRow.class)
                .optional()
                .map(this::withLines);
    }

    private WebOrder withLines(OrderRow o) {
        var lines = jdbc.sql("""
                        SELECT sku, product_slug, name, colour_name, size, addon_name, blouse_size, quantity, unit_rupees,
                               addon_rupees, amount_rupees, image_key
                        FROM web_order_line WHERE order_id = :id ORDER BY position
                        """).param("id", o.id()).query(WebOrder.Line.class).list();
        return new WebOrder(
                o.number(),
                o.status(),
                o.contactName(),
                o.contactPhone(),
                o.contactEmail(),
                o.addressLine1(),
                o.addressLine2(),
                o.city(),
                o.state(),
                o.pincode(),
                o.itemsRupees(),
                o.shippingRupees(),
                o.totalRupees(),
                o.razorpayPaymentId(),
                o.paidAt(),
                o.createdAt(),
                lines);
    }

    private @Nullable String razorpayOrderId(String number) {
        return jdbc.sql("SELECT razorpay_order_id FROM web_order WHERE number = :number")
                .param(NUMBER, number)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private static @Nullable String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

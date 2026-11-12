package com.vaabhi.checkout;

import com.vaabhi.catalog.BagQuote;
import com.vaabhi.catalog.BagQuoteRequest;
import com.vaabhi.catalog.BagQuoteService;
import com.vaabhi.catalog.CatalogStock;
import com.vaabhi.customer.CustomerService;
import com.vaabhi.money.Invoice;
import com.vaabhi.money.InvoiceParties;
import com.vaabhi.money.WebInvoiceRequest;
import com.vaabhi.money.WebInvoiceService;
import com.vaabhi.settings.NumberSequence;
import com.vaabhi.settings.NumberingService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Online orders: placed from the bag (paid with Razorpay, or cash on delivery), then shipped, delivered or
 * cancelled by the shop. Stock is taken when an order is PLACED and put back when it is cancelled; a cancelled
 * order that was paid online is refunded through Razorpay (the refund webhooks report when the money arrives).
 * Shipping is by hand or through Shiprocket ({@link ShiprocketShipping}), whose tracking updates mark the order
 * delivered. The customer gets a WhatsApp and an email at each step.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NUMBER = "number";
    private static final String CUSTOMER = "customer";
    private static final String ID = "id";

    private final JdbcClient jdbc;
    private final BagQuoteService quotes;
    private final CatalogStock stock;
    private final CustomerService customers;
    private final RazorpayClient razorpay;
    private final ShiprocketClient shiprocket;
    private final CheckoutProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final NumberingService numbering;
    private final WebInvoiceService webInvoices;

    CheckoutService(
            JdbcClient jdbc,
            BagQuoteService quotes,
            CatalogStock stock,
            CustomerService customers,
            RazorpayClient razorpay,
            ShiprocketClient shiprocket,
            CheckoutProperties properties,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            NumberingService numbering,
            WebInvoiceService webInvoices) {
        this.webInvoices = webInvoices;
        this.transactions = new TransactionTemplate(transactionManager);
        this.numbering = numbering;
        this.jdbc = jdbc;
        this.quotes = quotes;
        this.stock = stock;
        this.customers = customers;
        this.razorpay = razorpay;
        this.shiprocket = shiprocket;
        this.properties = properties;
        this.events = events;
    }

    // --- placing ------------------------------------------------------------------------------------------------

    /**
     * Prices the bag again on the server and saves the order. ONLINE: waits for payment and gets a Razorpay order.
     * COD: placed straight away (stock taken, WhatsApp sent). Fails with 409 when an item is no longer available in the
     * quantity asked for.
     */
    @Transactional
    public CheckoutStart start(long customerId, CheckoutRequest request) {
        var method = request.method();
        var keyId = method == WebOrder.PaymentMethod.ONLINE ? razorpay.keyId() : null;
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
        var pincode = request.address().pincode();
        if (shiprocket.serviceable(pincode, method == WebOrder.PaymentMethod.COD) == ShiprocketClient.Serviceable.NO) {
            throw conflict(
                    method == WebOrder.PaymentMethod.COD
                            ? "Cash on delivery is not available for pincode " + pincode + ". Please pay online."
                            : "We cannot deliver to pincode " + pincode + " yet.");
        }
        var items = quote.totalRupees();
        var shipping = items.compareTo(properties.freeShippingFromRupees()) >= 0
                ? Rupees.ZERO
                : Rupees.of(properties.shippingRupees());
        var total = items.add(shipping);
        var number = numbering.next(NumberSequence.Kind.WEB_ORDER);
        var address = request.address();
        var phone = "+91" + address.phone();
        var status = method == WebOrder.PaymentMethod.COD ? WebOrder.Status.PLACED : WebOrder.Status.PENDING_PAYMENT;
        long orderId = jdbc.sql("""
                        INSERT INTO web_order (number, customer_id, status, payment_method, contact_name, contact_phone,
                            contact_email, address_line1, address_line2, city, state, pincode, items_rupees,
                            shipping_rupees, total_rupees)
                        VALUES (:number, :customer, :status, :method, :name, :phone, :email, :line1, :line2, :city,
                            :state, :pincode, :items, :shipping, :total)
                        RETURNING id
                        """)
                .param(NUMBER, number)
                .param(CUSTOMER, customerId)
                .param("status", status.name())
                .param("method", method.name())
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
        String razorpayOrderId = null;
        if (method == WebOrder.PaymentMethod.ONLINE) {
            razorpayOrderId = razorpay.createOrder(total, number);
            jdbc.sql("UPDATE web_order SET razorpay_order_id = :rzp, payment_started_at = now() WHERE id = :id")
                    .param("rzp", razorpayOrderId)
                    .param(ID, orderId)
                    .update();
        } else {
            stock.take(soldBySku(orderId));
            publish(WebOrderEvent.Type.PLACED, number);
        }
        return new CheckoutStart(
                number,
                status,
                method,
                total,
                razorpayOrderId,
                keyId,
                RazorpayClient.paise(total),
                "INR",
                address.name().trim(),
                phone,
                profile.email());
    }

    /**
     * "Pay now" on an online order that was never paid (window closed, payment failed): opens the same Razorpay
     * order again at the price it was placed at, as long as every item is still available.
     */
    @Transactional
    public CheckoutStart payAgain(long customerId, String number) {
        var order = order(customerId, number);
        if (order.paymentMethod() != WebOrder.PaymentMethod.ONLINE
                || (order.status() != WebOrder.Status.PENDING_PAYMENT && order.status() != WebOrder.Status.FAILED)) {
            throw conflict("This order does not need paying (it is " + label(order.status()) + ")");
        }
        var razorpayOrderId = razorpayOrderId(number);
        if (razorpayOrderId == null) {
            throw conflict("This order cannot be paid online. Please place it again from your bag.");
        }
        var keyId = razorpay.keyId();
        var addons = jdbc.sql("SELECT addon_code FROM web_order_line WHERE order_id ="
                        + " (SELECT id FROM web_order WHERE number = :number) ORDER BY position")
                .param(NUMBER, number)
                .query((rs, n) -> Optional.ofNullable(rs.getString("addon_code")))
                .list();
        var lines = order.lines();
        List<BagQuoteRequest.Line> asked = new java.util.ArrayList<>();
        for (var i = 0; i < lines.size(); i++) {
            asked.add(new BagQuoteRequest.Line(
                    lines.get(i).sku(), addons.get(i).orElse(null), lines.get(i).quantity()));
        }
        for (var line : quotes.quote(new BagQuoteRequest(asked)).lines()) {
            if (line.status() != BagQuote.Status.AVAILABLE) {
                throw conflict((line.name() == null ? line.sku() : line.name())
                        + " is no longer available in this quantity. Please place a new order from your bag.");
            }
        }
        jdbc.sql("UPDATE web_order SET status = 'PENDING_PAYMENT', payment_started_at = now() WHERE number = :number")
                .param(NUMBER, number)
                .update();
        return new CheckoutStart(
                number,
                WebOrder.Status.PENDING_PAYMENT,
                WebOrder.PaymentMethod.ONLINE,
                order.totalRupees(),
                razorpayOrderId,
                keyId,
                RazorpayClient.paise(order.totalRupees()),
                "INR",
                order.contactName(),
                order.contactPhone(),
                order.contactEmail());
    }

    /**
     * The customer cancels an order that has not been shipped yet (refunded in full when paid online). Not one
     * transaction: see {@link #cancel}.
     */
    public WebOrder cancelByCustomer(long customerId, String number, CancelWebOrderForm form) {
        var order = order(customerId, number);
        if (order.status() != WebOrder.Status.PLACED) {
            throw conflict(
                    order.status() == WebOrder.Status.SHIPPED
                            ? "This order has already been shipped. You can return it once it is delivered."
                            : "This order cannot be cancelled (it is " + label(order.status()) + ")");
        }
        var reason = blankToNull(form.reason());
        cancel(
                number,
                new CancelWebOrderForm(
                        truncate("Cancelled by the customer" + (reason == null ? "" : ": " + reason), 200)));
        return order(customerId, number);
    }

    /** Records the payment Razorpay Checkout returned to the browser, once its signature checks out. */
    @Transactional
    public WebOrder confirm(long customerId, String number, PaymentConfirmation payment) {
        var order = order(customerId, number);
        if (order.status() != WebOrder.Status.PENDING_PAYMENT && order.status() != WebOrder.Status.FAILED) {
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
     * Razorpay's webhook: payment.captured / order.paid place the order (in case the browser never came back),
     * payment.failed marks a waiting order failed, refund.processed / refund.failed record where a refund is.
     * Unknown events and orders are ignored.
     */
    @Transactional
    public void webhook(String body, String signature) {
        if (!razorpay.webhookSignatureValid(body, signature)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
        }
        var event = JSON.readTree(body);
        var name = event.path("event").asString("");
        var payment = event.path("payload").path("payment").path("entity");
        var orderId = text(payment, "order_id");
        var paymentId = text(payment, ID);
        var refundId = text(event.path("payload").path("refund").path("entity"), ID);
        switch (name) {
            case "payment.captured", "order.paid" -> {
                if (orderId != null && paymentId != null) {
                    markPaid(orderId, paymentId);
                }
            }
            case "payment.failed" -> {
                if (orderId != null) {
                    jdbc.sql("UPDATE web_order SET status = 'FAILED' WHERE razorpay_order_id = :rzp"
                                    + " AND status = 'PENDING_PAYMENT'")
                            .param("rzp", orderId)
                            .update();
                }
            }
            case "refund.processed", "refund.failed" -> {
                if (refundId != null) {
                    var status = name.equals("refund.processed") ? "PROCESSED" : "FAILED";
                    jdbc.sql("UPDATE web_order SET refund_status = :status WHERE refund_id = :refund")
                            .param("status", status)
                            .param("refund", refundId)
                            .update();
                    jdbc.sql("""
                                    UPDATE web_order_return SET refund_status = :status
                                    WHERE refund_id = :refund AND refund_method = 'RAZORPAY'
                                    """)
                            .param("status", status)
                            .param("refund", refundId)
                            .update();
                }
            }
            default -> log.debug("Ignoring Razorpay event {}", LogSafe.clean(name));
        }
    }

    /** Online orders still waiting for payment after {@code olderThanMinutes}, oldest first (for reconciling). */
    @Transactional(readOnly = true)
    public List<String> waitingForPayment(int olderThanMinutes, int limit) {
        return jdbc.sql("""
                        SELECT number FROM web_order
                        WHERE status = 'PENDING_PAYMENT' AND razorpay_order_id IS NOT NULL
                          AND COALESCE(payment_started_at, created_at) < now() - make_interval(mins => :minutes)
                        ORDER BY COALESCE(payment_started_at, created_at) LIMIT :limit
                        """)
                .param("minutes", olderThanMinutes)
                .param("limit", limit)
                .query(String.class)
                .list();
    }

    /**
     * Asks Razorpay what happened to a waiting order: a captured payment places it, an authorized one (auto-capture
     * off) is captured first; with neither after {@code giveUpAfterMinutes} the order is marked FAILED (a payment
     * that still arrives later places it all the same).
     */
    @Transactional
    public void reconcilePayment(String number, int giveUpAfterMinutes) {
        var order = byNumber(number);
        var razorpayOrderId = razorpayOrderId(number);
        if (order.status() != WebOrder.Status.PENDING_PAYMENT || razorpayOrderId == null) {
            return;
        }
        var paise = RazorpayClient.paise(order.totalRupees());
        for (var payment : razorpay.orderPayments(razorpayOrderId, number)) {
            if (payment.status().equals("authorized") && payment.amountPaise() == paise) {
                razorpay.capture(payment.id(), paise, number);
                markPaid(razorpayOrderId, payment.id());
                return;
            }
            if (payment.status().equals("captured")) {
                markPaid(razorpayOrderId, payment.id());
                return;
            }
        }
        jdbc.sql("""
                        UPDATE web_order SET status = 'FAILED'
                        WHERE number = :number AND status = 'PENDING_PAYMENT'
                          AND COALESCE(payment_started_at, created_at) < now() - make_interval(mins => :minutes)
                        """).param(NUMBER, number).param("minutes", giveUpAfterMinutes).update();
    }

    /** Places a paid online order once (a repeat from the webhook or the browser does nothing) and takes stock. */
    private void markPaid(String razorpayOrderId, String razorpayPaymentId) {
        jdbc.sql("""
                        UPDATE web_order SET status = 'PLACED', razorpay_payment_id = :payment, paid_at = now()
                        WHERE razorpay_order_id = :rzp AND status IN ('PENDING_PAYMENT', 'FAILED')
                        RETURNING id, number
                        """)
                .param("payment", razorpayPaymentId)
                .param("rzp", razorpayOrderId)
                .query((rs, n) -> Map.entry(rs.getLong(ID), rs.getString(NUMBER)))
                .optional()
                .ifPresent(placed -> {
                    stock.take(soldBySku(placed.getKey()));
                    publish(WebOrderEvent.Type.PLACED, placed.getValue());
                });
    }

    // --- the shop's actions -------------------------------------------------------------------------------------

    /** Marks a placed order sent, with the courier and tracking number when there are any. */
    @Transactional
    public WebOrder ship(String number, ShipmentForm form) {
        var order = byNumber(number);
        if (order.status() != WebOrder.Status.PLACED) {
            throw conflict("Only a placed order can be shipped (this one is " + label(order.status()) + ")");
        }
        jdbc.sql("""
                        UPDATE web_order SET status = 'SHIPPED', shipped_at = now(), courier = :courier,
                            tracking_number = :tracking
                        WHERE number = :number
                        """)
                .param("courier", blankToNull(form.courier()))
                .param("tracking", blankToNull(form.trackingNumber()))
                .param(NUMBER, number)
                .update();
        invoiceFor(number);
        publish(WebOrderEvent.Type.SHIPPED, number);
        return byNumber(number);
    }

    /** The order's live invoice, issued now when it has none (so shipping again reuses the same number). */
    @Transactional
    Invoice invoiceFor(String number) {
        var order = byNumber(number);
        var row = jdbc.sql("""
                        SELECT o.id, c.party_id FROM web_order o
                        LEFT JOIN customer c ON c.id = o.customer_id
                        WHERE o.number = :number
                        """)
                .param(NUMBER, number)
                .query((rs, n) -> new OrderParty(rs.getLong("id"), rs.getObject("party_id", Long.class)))
                .single();
        var address = jdbc.sql("""
                        SELECT left(concat_ws(', ', address_line1, address_line2, city, pincode), 400)
                        FROM web_order WHERE id = :id
                        """).param(ID, row.id()).query(String.class).single();
        var lines = new ArrayList<WebInvoiceRequest.Line>();
        for (var line : order.lines()) {
            var detail = line.colourName() != null && line.size() != null
                    ? " (" + line.colourName() + ", " + line.size() + ")"
                    : line.colourName() != null
                            ? " (" + line.colourName() + ")"
                            : line.size() != null ? " (" + line.size() + ")" : "";
            lines.add(new WebInvoiceRequest.Line(line.name() + detail, line.quantity(), line.unitRupees()));
            if (line.addonRupees().signum() > 0) {
                lines.add(new WebInvoiceRequest.Line(
                        line.addonName() + " for " + line.name(), line.quantity(), line.addonRupees()));
            }
        }
        return webInvoices.issueFor(new WebInvoiceRequest(
                row.id(),
                number,
                row.partyId(),
                new InvoiceParties.Buyer(order.contactName(), order.contactPhone(), address, order.state()),
                lines,
                order.shippingRupees()));
    }

    private record OrderParty(long id, @Nullable Long partyId) {}

    /** Marks the order delivered; for cash on delivery this is when the money came in. */
    @Transactional
    public WebOrder deliver(String number) {
        var order = byNumber(number);
        if (order.status() != WebOrder.Status.PLACED && order.status() != WebOrder.Status.SHIPPED) {
            throw conflict(
                    "Only a placed or shipped order can be delivered (this one is " + label(order.status()) + ")");
        }
        if (order.status() == WebOrder.Status.PLACED) {
            invoiceFor(number);
        }
        jdbc.sql("""
                        UPDATE web_order SET status = 'DELIVERED', delivered_at = now(),
                            paid_at = CASE WHEN payment_method = 'COD' THEN now() ELSE paid_at END
                        WHERE number = :number
                        """).param(NUMBER, number).update();
        publish(WebOrderEvent.Type.DELIVERED, number);
        return byNumber(number);
    }

    /**
     * Cancels a placed or shipped order: the items go back into stock and, when it was paid online, the whole
     * amount is refunded through Razorpay.
     *
     * <p>Deliberately not one transaction, because Shiprocket and Razorpay can't be rolled back. (1) The pickup is
     * cancelled in Shiprocket; a shipped parcel the courier already has is refused, so no refund goes out while it
     * is on its way. (2) One short transaction saves the cancel, the refund owed (PENDING, no refund id yet) and the
     * stock. (3) Only after that commit is the refund sent ({@link #sendRefund}); if Razorpay refuses, the order
     * stays cancelled with the refund FAILED and the owner can send it again ({@link #retryRefund}).
     */
    public WebOrder cancel(String number, CancelWebOrderForm form) {
        var order = byNumber(number);
        if (order.status() != WebOrder.Status.PLACED && order.status() != WebOrder.Status.SHIPPED) {
            throw conflict(
                    "Only a placed or shipped order can be cancelled (this one is " + label(order.status()) + ")");
        }
        var trackingStatus = order.trackingStatus();
        var shiprocketOrderId = shipment(number).orderId();
        if (shiprocketOrderId != null) {
            // The courier must not pick it up; if Shiprocket refuses (e.g. already picked up) the shop sees why.
            try {
                shiprocket.cancel(shiprocketOrderId, number);
                trackingStatus = "Cancelled in Shiprocket";
            } catch (ResponseStatusException e) {
                if (order.status() == WebOrder.Status.SHIPPED) {
                    throw conflict("Shiprocket could not cancel the pickup (" + e.getReason()
                            + "). Cancel it in Shiprocket first, or wait for the parcel to come back.");
                }
                trackingStatus = "Not cancelled in Shiprocket, cancel it there (" + e.getReason() + ")";
            }
        }
        var refundOwed = order.paymentMethod() == WebOrder.PaymentMethod.ONLINE && order.razorpayPaymentId() != null
                ? order.totalRupees()
                : null;
        var tracking = trackingStatus == null ? null : truncate(trackingStatus, 120);
        transactions.executeWithoutResult(status -> {
            long id = jdbc.sql("""
                            UPDATE web_order SET status = 'CANCELLED', cancelled_at = now(), cancel_reason = :reason,
                                refunded_rupees = :refunded,
                                refund_status = CASE WHEN CAST(:refunded AS numeric) IS NULL THEN NULL
                                                     ELSE 'PENDING' END,
                                refund_id = NULL, refund_error = NULL, refunded_at = NULL, tracking_status = :tracking
                            WHERE number = :number AND status IN ('PLACED', 'SHIPPED')
                            RETURNING id
                            """)
                    .param("reason", blankToNull(form.reason()))
                    .param("refunded", refundOwed)
                    .param("tracking", tracking)
                    .param(NUMBER, number)
                    .query(Long.class)
                    .optional()
                    .orElseThrow(() -> conflict("This order was cancelled or moved on just now. Reload it."));
            stock.restore(soldBySku(id));
            webInvoices.cancelFor(id, null);
            publish(WebOrderEvent.Type.CANCELLED, number);
        });
        if (refundOwed != null) {
            sendRefund(number);
        }
        return byNumber(number);
    }

    /** Sends a cancelled order's refund again (the owner's button when it failed or never reached Razorpay). */
    public WebOrder retryRefund(String number) {
        var order = byNumber(number);
        if (order.status() != WebOrder.Status.CANCELLED
                || order.razorpayPaymentId() == null
                || order.refundedRupees() == null) {
            throw conflict("This order has no refund to send");
        }
        if (order.refundId() != null && order.refundStatus() != WebOrder.RefundStatus.FAILED) {
            throw conflict("Razorpay already has this refund (" + order.refundId() + ")");
        }
        sendRefund(number);
        return byNumber(number);
    }

    /**
     * Sends the refund a cancelled order is owed, outside any transaction. Reuses a refund Razorpay already made for
     * this amount (a timed-out earlier try), so calling it again never refunds twice. A refusal is saved, not thrown.
     */
    private void sendRefund(String number) {
        var order = byNumber(number);
        var paymentId = order.razorpayPaymentId();
        var amount = order.refundedRupees();
        if (paymentId == null || amount == null) {
            return;
        }
        try {
            var refund = razorpay.existingRefund(paymentId, amount, number)
                    .orElseGet(() -> razorpay.refund(paymentId, amount, number));
            jdbc.sql("""
                            UPDATE web_order SET refund_id = :refund, refund_status = :status, refund_error = NULL,
                                refunded_at = now()
                            WHERE number = :number
                            """)
                    .param("refund", refund.id())
                    .param("status", refund.status().equals("processed") ? "PROCESSED" : "PENDING")
                    .param(NUMBER, number)
                    .update();
        } catch (ResponseStatusException e) {
            log.warn("Refund for cancelled order {} failed: {}", LogSafe.clean(number), LogSafe.clean(e.getReason()));
            // Only when no refund got through meanwhile (a second click that Razorpay refused must not undo it).
            jdbc.sql("""
                            UPDATE web_order SET refund_status = 'FAILED', refund_error = :error
                            WHERE number = :number AND (refund_id IS NULL OR refund_status = 'FAILED')
                            """)
                    .param(
                            "error",
                            truncate(e.getReason() == null ? "Razorpay could not be reached" : e.getReason(), 200))
                    .param(NUMBER, number)
                    .update();
        }
    }

    // --- Shiprocket ----------------------------------------------------------------------------------------------

    /** Where the order is in Shiprocket: nothing yet, created there, or with an AWB too. */
    record Shipment(
            @Nullable Long orderId,
            @Nullable Long shipmentId,
            @Nullable String awb) {}

    @Transactional(readOnly = true)
    public Shipment shipment(String number) {
        return jdbc.sql("""
                        SELECT shiprocket_order_id AS order_id, shiprocket_shipment_id AS shipment_id,
                               CASE WHEN shiprocket_shipment_id IS NULL THEN NULL ELSE tracking_number END AS awb
                        FROM web_order WHERE number = :number
                        """)
                .param(NUMBER, number)
                .query(Shipment.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    /** Saves the Shiprocket ids straight away, so a retry after a later step fails does not create it twice. */
    @Transactional
    public void recordShiprocketOrder(String number, long shiprocketOrderId, long shipmentId) {
        jdbc.sql("""
                        UPDATE web_order SET shiprocket_order_id = :order, shiprocket_shipment_id = :shipment
                        WHERE number = :number
                        """)
                .param("order", shiprocketOrderId)
                .param("shipment", shipmentId)
                .param(NUMBER, number)
                .update();
    }

    @Transactional
    public void recordShippingLabel(String number, String labelUrl) {
        jdbc.sql("UPDATE web_order SET shipping_label_url = :url WHERE number = :number")
                .param("url", truncate(labelUrl, 500))
                .param(NUMBER, number)
                .update();
    }

    /**
     * The courier's latest status for an AWB (Shiprocket's webhook or a refresh). DELIVERED marks the order
     * delivered (and a cash-on-delivery order paid); other statuses are only shown.
     */
    @Transactional
    public void trackingUpdate(String awb, String status) {
        var number = jdbc.sql("""
                        UPDATE web_order SET tracking_status = :status, tracking_updated_at = now()
                        WHERE tracking_number = :awb AND status <> 'CANCELLED'
                        RETURNING number
                        """)
                .param("status", truncate(status, 120))
                .param("awb", awb)
                .query(String.class)
                .optional();
        if (number.isEmpty()) {
            // Perhaps a return pickup: the status is only shown, the shop marks the items received.
            var returns = jdbc.sql("UPDATE web_order_return SET pickup_status = :status WHERE pickup_awb = :awb")
                    .param("status", truncate(status, 120))
                    .param("awb", awb)
                    .update();
            if (returns == 0) {
                log.debug("Tracking update for unknown AWB {}", LogSafe.clean(awb));
            }
            return;
        }
        var order = byNumber(number.get());
        if (status.trim().equalsIgnoreCase("DELIVERED")
                && (order.status() == WebOrder.Status.PLACED || order.status() == WebOrder.Status.SHIPPED)) {
            deliver(order.number());
        }
    }

    // --- reading ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<WebOrder> orders(long customerId) {
        var rows = jdbc.sql(SELECT + " WHERE o.customer_id = :customer ORDER BY o.created_at DESC LIMIT 50")
                .param(CUSTOMER, customerId)
                .query(OrderRow.class)
                .list();
        return withLines(rows);
    }

    @Transactional(readOnly = true)
    public WebOrder order(long customerId, String number) {
        return find(
                        " WHERE o.number = :number AND o.customer_id = :customer",
                        Map.of(NUMBER, number, CUSTOMER, customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    /** The shop's view, newest first; optionally only orders in one status, or with a return still open. */
    @Transactional(readOnly = true)
    public List<WebOrder> recent(WebOrder.@Nullable Status status, boolean openReturns, int limit) {
        var rows = jdbc.sql(SELECT + " WHERE (CAST(:status AS varchar) IS NULL OR o.status = :status)"
                        + " AND (NOT :openReturns OR EXISTS (SELECT 1 FROM web_order_return r WHERE r.order_id = o.id"
                        + " AND r.status IN ('REQUESTED', 'APPROVED')))"
                        + " ORDER BY o.created_at DESC LIMIT :limit")
                .param("status", status == null ? null : status.name())
                .param("openReturns", openReturns)
                .param("limit", limit)
                .query(OrderRow.class)
                .list();
        return withLines(rows);
    }

    /** Any online order, for the shop. */
    @Transactional(readOnly = true)
    public WebOrder shopOrder(String number) {
        return byNumber(number);
    }

    /** Any online order by its number, for guest tracking (the caller checks the phone). */
    @Transactional(readOnly = true)
    Optional<WebOrder> findByNumber(String number) {
        return find(" WHERE o.number = :number", Map.of(NUMBER, number));
    }

    /** When the courier status was last saved (webhook or refresh); null when it never was. */
    @Transactional(readOnly = true)
    @Nullable
    OffsetDateTime trackingUpdatedAt(String number) {
        return jdbc.sql("SELECT tracking_updated_at FROM web_order WHERE number = :number")
                .param(NUMBER, number)
                .query(OffsetDateTime.class)
                .optional()
                .orElse(null);
    }

    private WebOrder byNumber(String number) {
        return find(" WHERE o.number = :number", Map.of(NUMBER, number))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private static final String SELECT = """
            SELECT o.id, o.number, o.status, o.payment_method, o.contact_name, o.contact_phone, o.contact_email,
                   o.address_line1, o.address_line2, o.city, o.state, o.pincode, o.items_rupees, o.shipping_rupees,
                   o.total_rupees, o.razorpay_payment_id, o.paid_at, o.courier, o.tracking_number, o.shipped_at,
                   o.delivered_at, o.cancelled_at, o.cancel_reason, o.refunded_rupees, o.refund_id, o.refund_status,
                   o.refund_error, o.tracking_status, o.shiprocket_shipment_id, o.shipping_label_url, o.created_at
            FROM web_order o
            """;

    private record OrderRow(
            long id,
            String number,
            WebOrder.Status status,
            WebOrder.PaymentMethod paymentMethod,
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
            WebOrder.@Nullable RefundStatus refundStatus,
            @Nullable String refundError,
            @Nullable String trackingStatus,
            @Nullable Long shiprocketShipmentId,
            @Nullable String shippingLabelUrl,
            OffsetDateTime createdAt) {}

    // Only constant SQL fragments (and fixed table names / enum-chosen ORDER BY) are joined; values are bound.
    @SuppressWarnings("java:S2077")
    private Optional<WebOrder> find(String where, Map<String, Object> params) {
        return jdbc.sql(SELECT + where)
                .params(params)
                .query(OrderRow.class)
                .optional()
                .map(o -> withLines(o, webInvoices.liveNumbers(List.of(o.id())).get(o.id())));
    }

    private List<WebOrder> withLines(List<OrderRow> rows) {
        var numbers = webInvoices.liveNumbers(rows.stream().map(OrderRow::id).toList());
        return rows.stream().map(o -> withLines(o, numbers.get(o.id()))).toList();
    }

    private WebOrder withLines(OrderRow o, @Nullable String invoiceNumber) {
        var lines = jdbc.sql("""
                        SELECT position, sku, product_slug, name, colour_name, size, addon_name, blouse_size, quantity,
                               unit_rupees, addon_rupees, amount_rupees, image_key
                        FROM web_order_line WHERE order_id = :id ORDER BY position
                        """).param(ID, o.id()).query(WebOrder.Line.class).list();
        var returnableUntil = o.status() == WebOrder.Status.DELIVERED && o.deliveredAt() != null
                ? o.deliveredAt().plusDays(properties.returnDays())
                : null;
        return new WebOrder(
                o.number(),
                o.status(),
                o.paymentMethod(),
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
                o.courier(),
                o.trackingNumber(),
                o.shippedAt(),
                o.deliveredAt(),
                o.cancelledAt(),
                o.cancelReason(),
                o.refundedRupees(),
                o.refundId(),
                o.refundStatus(),
                o.refundError(),
                o.trackingStatus(),
                o.shiprocketShipmentId() != null && o.trackingNumber() != null
                        ? "https://shiprocket.co/tracking/" + o.trackingNumber()
                        : null,
                o.shippingLabelUrl(),
                returnableUntil,
                o.createdAt(),
                lines,
                returns(o.id()),
                invoiceNumber);
    }

    private record ReturnRow(
            long id,
            String number,
            WebOrder.ReturnStatus status,
            String reason,
            @Nullable String shopNote,
            BigDecimal itemsRupees,
            @Nullable BigDecimal refundRupees,
            WebOrder.@Nullable RefundMethod refundMethod,
            @Nullable String refundId,
            WebOrder.@Nullable RefundStatus refundStatus,
            @Nullable String pickupCourier,
            @Nullable String pickupAwb,
            @Nullable String pickupStatus,
            OffsetDateTime requestedAt,
            @Nullable OffsetDateTime refundedAt) {}

    private List<WebOrder.OrderReturn> returns(long orderId) {
        return jdbc.sql("""
                        SELECT id, number, status, reason, shop_note, items_rupees, refund_rupees, refund_method,
                               refund_id, refund_status, pickup_courier, pickup_awb, pickup_status, requested_at,
                               refunded_at
                        FROM web_order_return WHERE order_id = :id ORDER BY requested_at
                        """).param(ID, orderId).query(ReturnRow.class).list().stream()
                .map(r -> new WebOrder.OrderReturn(
                        r.number(),
                        r.status(),
                        r.reason(),
                        r.shopNote(),
                        r.itemsRupees(),
                        r.refundRupees(),
                        r.refundMethod(),
                        r.refundId(),
                        r.refundStatus(),
                        r.pickupCourier(),
                        r.pickupAwb(),
                        r.pickupStatus(),
                        r.requestedAt(),
                        r.refundedAt(),
                        jdbc.sql("""
                                        SELECT l.position, l.name, rl.quantity
                                        FROM web_order_return_line rl JOIN web_order_line l ON l.id = rl.order_line_id
                                        WHERE rl.return_id = :id ORDER BY l.position
                                        """)
                                .param(ID, r.id())
                                .query(WebOrder.ReturnLine.class)
                                .list()))
                .toList();
    }

    /** Quantities of the order's lines by SKU (one SKU can appear with different add-ons). */
    private Map<String, Integer> soldBySku(long orderId) {
        Map<String, Integer> sold = new LinkedHashMap<>();
        jdbc.sql("SELECT sku, quantity FROM web_order_line WHERE order_id = :id")
                .param(ID, orderId)
                .query((rs, n) -> Map.entry(rs.getString("sku"), rs.getInt("quantity")))
                .list()
                .forEach(e -> sold.merge(e.getKey(), e.getValue(), Integer::sum));
        return sold;
    }

    private void publish(WebOrderEvent.Type type, String number) {
        var order = byNumber(number);
        events.publishEvent(new WebOrderEvent(
                type,
                number,
                order.contactPhone(),
                order.totalRupees(),
                order.paymentMethod(),
                order.courier(),
                order.trackingNumber(),
                order.refundedRupees()));
    }

    private @Nullable String razorpayOrderId(String number) {
        return jdbc.sql("SELECT razorpay_order_id FROM web_order WHERE number = :number")
                .param(NUMBER, number)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private static String label(WebOrder.Status status) {
        return status.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static @Nullable String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

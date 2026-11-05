package com.vaabhi.checkout;

import com.vaabhi.catalog.CatalogStock;
import com.vaabhi.shared.rupees.Rupees;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Returns of delivered online orders. The customer asks (some or all items, within the return window); the shop
 * approves or rejects, optionally books the pickup through Shiprocket, and once the items are back refunds them:
 * part of the Razorpay payment for an order paid online, or by UPI / bank (reference recorded) for cash on
 * delivery. The returned items go back into stock then.
 */
@Service
public class WebOrderReturns {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");
    private static final String NUMBER = "number";

    private final JdbcClient jdbc;
    private final CheckoutService orders;
    private final RazorpayClient razorpay;
    private final CatalogStock stock;
    private final ApplicationEventPublisher events;

    WebOrderReturns(
            JdbcClient jdbc,
            CheckoutService orders,
            RazorpayClient razorpay,
            CatalogStock stock,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.razorpay = razorpay;
        this.stock = stock;
        this.events = events;
    }

    /** The customer's request; at most the quantities delivered and not already being returned. */
    @Transactional
    public WebOrder request(long customerId, String number, ReturnRequestForm form) {
        var order = orders.order(customerId, number);
        if (order.status() != WebOrder.Status.DELIVERED || order.returnableUntil() == null) {
            throw conflict("Only a delivered order can be returned");
        }
        if (OffsetDateTime.now().isAfter(order.returnableUntil())) {
            throw conflict("Returns for this order closed on " + DAY.format(order.returnableUntil()));
        }
        Map<Integer, Integer> asked = new LinkedHashMap<>();
        form.items().forEach(i -> asked.merge(i.position(), i.quantity(), Integer::sum));
        Map<Integer, Integer> already = new LinkedHashMap<>();
        order.returns().stream()
                .filter(r -> r.status() != WebOrder.ReturnStatus.REJECTED)
                .flatMap(r -> r.lines().stream())
                .forEach(l -> already.merge(l.position(), l.quantity(), Integer::sum));
        var items = Rupees.ZERO;
        for (var entry : asked.entrySet()) {
            var line = order.lines().stream()
                    .filter(l -> l.position() == entry.getKey())
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown item"));
            var left = line.quantity() - already.getOrDefault(line.position(), 0);
            if (entry.getValue() > left) {
                throw conflict(
                        left <= 0
                                ? line.name() + " is already being returned"
                                : "You can return at most " + left + " of " + line.name());
            }
            items = items.add(Rupees.times(line.unitRupees().add(line.addonRupees()), entry.getValue()));
        }
        long orderId = orderId(number);
        var returnNumber = number + "-R" + (order.returns().size() + 1);
        long returnId = jdbc.sql("""
                        INSERT INTO web_order_return (order_id, number, reason, items_rupees)
                        VALUES (:order, :number, :reason, :items)
                        RETURNING id
                        """)
                .param("order", orderId)
                .param(NUMBER, returnNumber)
                .param("reason", form.reason().trim())
                .param("items", items)
                .query(Long.class)
                .single();
        asked.forEach((position, quantity) -> jdbc.sql("""
                        INSERT INTO web_order_return_line (return_id, order_line_id, quantity)
                        SELECT :return, id, :quantity FROM web_order_line WHERE order_id = :order AND position = :position
                        """)
                .param("return", returnId)
                .param("quantity", quantity)
                .param("order", orderId)
                .param("position", position)
                .update());
        return orders.order(customerId, number);
    }

    @Transactional
    public WebOrder approve(String returnNumber, ReturnDecisionForm form) {
        var current = find(returnNumber);
        if (current.status() != WebOrder.ReturnStatus.REQUESTED) {
            throw conflict("Only a requested return can be approved");
        }
        decide(returnNumber, "APPROVED", form.note());
        publish(WebOrderReturnEvent.Type.APPROVED, current.orderNumber(), returnNumber);
        return orders.shopOrder(current.orderNumber());
    }

    @Transactional
    public WebOrder reject(String returnNumber, ReturnDecisionForm form) {
        var current = find(returnNumber);
        if (current.status() != WebOrder.ReturnStatus.REQUESTED && current.status() != WebOrder.ReturnStatus.APPROVED) {
            throw conflict("This return is already closed");
        }
        decide(returnNumber, "REJECTED", form.note());
        publish(WebOrderReturnEvent.Type.REJECTED, current.orderNumber(), returnNumber);
        return orders.shopOrder(current.orderNumber());
    }

    /**
     * The items are back: puts them into stock and refunds {@code amountRupees}. Online orders are refunded through
     * Razorpay (a partial refund of the payment); cash-on-delivery orders need the reference of the money sent.
     */
    @Transactional
    public WebOrder refund(String returnNumber, ReturnRefundForm form) {
        var current = find(returnNumber);
        if (current.status() != WebOrder.ReturnStatus.REQUESTED && current.status() != WebOrder.ReturnStatus.APPROVED) {
            throw conflict("This return is already closed");
        }
        var order = orders.shopOrder(current.orderNumber());
        var amount = Rupees.of(form.amountRupees());
        var refundedBefore = Rupees.orZero(order.refundedRupees())
                .add(Rupees.sum(
                        order.returns().stream()
                                .filter(r -> r.refundRupees() != null)
                                .toList(),
                        WebOrder.OrderReturn::refundRupees));
        var refundable = order.totalRupees().subtract(refundedBefore);
        if (amount.compareTo(refundable) > 0) {
            throw conflict("At most " + refundable.toPlainString() + " can still be refunded on this order");
        }
        WebOrder.RefundMethod method;
        String reference;
        String status;
        if (order.paymentMethod() == WebOrder.PaymentMethod.ONLINE && order.razorpayPaymentId() != null) {
            method = WebOrder.RefundMethod.RAZORPAY;
            if (Rupees.isPositive(amount)) {
                var refund = razorpay.refund(order.razorpayPaymentId(), amount, returnNumber);
                reference = refund.id();
                status = refund.status().equals("processed") ? "PROCESSED" : "PENDING";
            } else {
                reference = null;
                status = null;
            }
        } else {
            method = WebOrder.RefundMethod.MANUAL;
            reference = blankToNull(form.reference());
            if (reference == null && Rupees.isPositive(amount)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Enter the UPI or bank reference of the money you sent back");
            }
            status = reference == null ? null : "PROCESSED";
        }
        jdbc.sql("""
                        UPDATE web_order_return SET status = 'REFUNDED', refund_rupees = :amount, refund_method = :method,
                            refund_id = :reference, refund_status = :status, refunded_at = now(),
                            decided_at = COALESCE(decided_at, now())
                        WHERE number = :number
                        """)
                .param("amount", amount)
                .param("method", method.name())
                .param("reference", reference)
                .param("status", status)
                .param(NUMBER, returnNumber)
                .update();
        Map<String, Integer> back = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT l.sku, rl.quantity FROM web_order_return_line rl
                        JOIN web_order_line l ON l.id = rl.order_line_id
                        JOIN web_order_return r ON r.id = rl.return_id
                        WHERE r.number = :number
                        """)
                .param(NUMBER, returnNumber)
                .query((rs, n) -> Map.entry(rs.getString("sku"), rs.getInt("quantity")))
                .list()
                .forEach(e -> back.merge(e.getKey(), e.getValue(), Integer::sum));
        stock.restore(back);
        publish(WebOrderReturnEvent.Type.REFUNDED, current.orderNumber(), returnNumber);
        return orders.shopOrder(current.orderNumber());
    }

    // --- for the Shiprocket return pickup ------------------------------------------------------------------------

    record ReturnRef(
            String orderNumber,
            WebOrder.ReturnStatus status,
            @Nullable Long shipmentId,
            @Nullable String pickupAwb) {}

    @Transactional(readOnly = true)
    public ReturnRef find(String returnNumber) {
        return jdbc.sql("""
                        SELECT o.number AS order_number, r.status, r.shiprocket_shipment_id AS shipment_id, r.pickup_awb
                        FROM web_order_return r JOIN web_order o ON o.id = r.order_id
                        WHERE r.number = :number
                        """)
                .param(NUMBER, returnNumber)
                .query(ReturnRef.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return not found"));
    }

    @Transactional
    public void recordPickupOrder(String returnNumber, long shiprocketOrderId, long shipmentId) {
        jdbc.sql("""
                        UPDATE web_order_return SET shiprocket_order_id = :order, shiprocket_shipment_id = :shipment
                        WHERE number = :number
                        """)
                .param("order", shiprocketOrderId)
                .param("shipment", shipmentId)
                .param(NUMBER, returnNumber)
                .update();
    }

    @Transactional
    public void recordPickupAwb(String returnNumber, @Nullable String courier, String awb) {
        jdbc.sql("""
                        UPDATE web_order_return SET pickup_courier = :courier, pickup_awb = :awb,
                            pickup_status = 'Pickup booked'
                        WHERE number = :number
                        """)
                .param("courier", courier == null ? null : courier.substring(0, Math.min(60, courier.length())))
                .param("awb", awb)
                .param(NUMBER, returnNumber)
                .update();
        publish(WebOrderReturnEvent.Type.PICKUP_BOOKED, find(returnNumber).orderNumber(), returnNumber);
    }

    private void publish(WebOrderReturnEvent.Type type, String orderNumber, String returnNumber) {
        events.publishEvent(new WebOrderReturnEvent(type, orderNumber, returnNumber));
    }

    private void decide(String returnNumber, String status, @Nullable String note) {
        jdbc.sql("""
                        UPDATE web_order_return SET status = :status, shop_note = COALESCE(CAST(:note AS varchar), shop_note),
                            decided_at = now()
                        WHERE number = :number
                        """)
                .param("status", status)
                .param("note", blankToNull(note))
                .param(NUMBER, returnNumber)
                .update();
    }

    private long orderId(String number) {
        return jdbc.sql("SELECT id FROM web_order WHERE number = :number")
                .param(NUMBER, number)
                .query(Long.class)
                .single();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

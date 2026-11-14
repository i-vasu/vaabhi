package com.vaabhi.checkout;

import com.vaabhi.catalog.CatalogStock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ships a placed order through Shiprocket in one go: create the order there, assign a courier (AWB), mark the order
 * shipped (the customer gets the tracking WhatsApp), schedule the pickup and make the label. Each step is saved as it
 * succeeds, so pressing the button again after a failure carries on where it stopped. Not transactional on purpose:
 * the Shiprocket ids must stay saved even when a later step fails.
 */
@Service
class ShiprocketShipping {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(ShiprocketShipping.class);

    private final CheckoutService orders;
    private final WebOrderReturns returns;
    private final ShiprocketClient shiprocket;
    private final CatalogStock catalog;
    private final TransactionTemplate transactions;

    ShiprocketShipping(
            CheckoutService orders,
            WebOrderReturns returns,
            ShiprocketClient shiprocket,
            CatalogStock catalog,
            PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.orders = orders;
        this.returns = returns;
        this.shiprocket = shiprocket;
        this.catalog = catalog;
    }

    WebOrder ship(String number) {
        var order = orders.shopOrder(number);
        if (order.status() != WebOrder.Status.PLACED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only an order that is still to ship can be sent with Shiprocket");
        }
        var shipment = orders.shipment(number);
        var invoice = Objects.requireNonNull(transactions.execute(status -> orders.invoiceFor(number)));
        long shipmentId;
        if (shipment.shipmentId() == null) {
            var created = shiprocket.createOrder(order, weightKg(order, null), invoice.number());
            orders.recordShiprocketOrder(number, created.orderId(), created.shipmentId());
            shipmentId = created.shipmentId();
        } else {
            shipmentId = shipment.shipmentId();
        }
        var awb = shiprocket.assignAwb(shipmentId, number);
        var courier = awb.courierName();
        if (courier != null && courier.length() > 60) {
            courier = courier.substring(0, 60);
        }
        orders.ship(number, new ShipmentForm(courier, awb.awbCode()));
        shiprocket.requestPickup(shipmentId, number);
        var label = shiprocket.label(shipmentId, number);
        if (label != null) {
            orders.recordShippingLabel(number, label);
        }
        return orders.shopOrder(number);
    }

    /**
     * For guest tracking: the order with the courier's latest status when ours is older than {@code maxAge}. A
     * shipped order only, with a Shiprocket AWB. If Shiprocket can't answer, the order as it was, since the status we
     * have is still worth showing.
     */
    WebOrder withFreshTracking(WebOrder order, Duration maxAge, OffsetDateTime now) {
        if (order.status() != WebOrder.Status.SHIPPED || order.trackingUrl() == null || !shiprocket.configured()) {
            return order;
        }
        var updatedAt = orders.trackingUpdatedAt(order.number());
        if (updatedAt != null && updatedAt.isAfter(now.minus(maxAge))) {
            return order;
        }
        try {
            return refreshTracking(order.number());
        } catch (RuntimeException e) {
            log.warn("Live tracking refresh failed for {}: {}", order.number(), e.getMessage());
            return order;
        }
    }

    /** Asks Shiprocket for the latest courier status now (for when the webhook is not set up or was missed). */
    WebOrder refreshTracking(String number) {
        var shipment = orders.shipment(number);
        if (shipment.awb() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order was not shipped with Shiprocket");
        }
        var status = shiprocket.trackingStatus(shipment.awb(), number);
        if (status != null) {
            orders.trackingUpdate(shipment.awb(), status);
        }
        return orders.shopOrder(number);
    }

    /**
     * Shiprocket's tracking webhook: one JSON object per status change, with the AWB and the courier's current
     * status. Answers 401 for a wrong token; anything it cannot use is ignored (Shiprocket only needs a 200).
     */
    void webhook(String body, String token) {
        if (!shiprocket.webhookTokenValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        JsonNode event;
        try {
            event = JSON.readTree(body);
        } catch (JacksonException e) {
            return; // e.g. Shiprocket's test call when the webhook is saved
        }
        var awb = ShiprocketClient.text(event.path("awb"));
        var status = ShiprocketClient.text(event.path("current_status"));
        if (status == null) {
            status = ShiprocketClient.text(event.path("shipment_status"));
        }
        if (awb != null && status != null) {
            orders.trackingUpdate(awb, status);
        }
    }

    /**
     * Books the courier to collect an approved return from the customer and bring it back to the pickup address.
     * Like shipping, a retry after a failed step carries on where it stopped.
     */
    WebOrder bookReturnPickup(String returnNumber) {
        var ref = returns.find(returnNumber);
        if (ref.status() != WebOrder.ReturnStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approve the return before booking its pickup");
        }
        if (ref.pickupAwb() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The pickup is already booked");
        }
        var order = orders.shopOrder(ref.orderNumber());
        var request = order.returns().stream()
                .filter(r -> r.number().equals(returnNumber))
                .findFirst()
                .orElseThrow();
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        request.lines().forEach(l -> quantities.merge(l.position(), l.quantity(), Integer::sum));
        long shipmentId;
        if (ref.shipmentId() == null) {
            var created = shiprocket.createReturn(order, returnNumber, quantities, weightKg(order, quantities));
            returns.recordPickupOrder(returnNumber, created.orderId(), created.shipmentId());
            shipmentId = created.shipmentId();
        } else {
            shipmentId = ref.shipmentId();
        }
        var awb = shiprocket.assignAwb(shipmentId, returnNumber, true);
        returns.recordPickupAwb(returnNumber, awb.courierName(), awb.awbCode());
        return orders.shopOrder(ref.orderNumber());
    }

    /**
     * The parcel weight: each product's packed weight (or the default) times the quantity.
     *
     * @param quantities pieces by line position; null for the whole order
     */
    private BigDecimal weightKg(WebOrder order, @Nullable Map<Integer, Integer> quantities) {
        var weights = catalog.shippingWeightGrams(
                order.lines().stream().map(WebOrder.Line::sku).toList());
        long grams = 0;
        for (var line : order.lines()) {
            int units = quantities == null ? line.quantity() : quantities.getOrDefault(line.position(), 0);
            grams += (long) units * weights.getOrDefault(line.sku(), shiprocket.defaultWeightGrams());
        }
        return BigDecimal.valueOf(Math.max(grams, 100)).divide(BigDecimal.valueOf(1000), 2, RoundingMode.UP);
    }
}

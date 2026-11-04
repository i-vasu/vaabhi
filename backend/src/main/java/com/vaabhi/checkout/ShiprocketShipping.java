package com.vaabhi.checkout;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ships a placed order through Shiprocket in one go: create the order there, assign a courier (AWB), mark the order
 * shipped (the customer gets the tracking SMS), schedule the pickup and make the label. Each step is saved as it
 * succeeds, so pressing the button again after a failure carries on where it stopped. Not transactional on purpose:
 * the Shiprocket ids must stay saved even when a later step fails.
 */
@Service
class ShiprocketShipping {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CheckoutService orders;
    private final ShiprocketClient shiprocket;

    ShiprocketShipping(CheckoutService orders, ShiprocketClient shiprocket) {
        this.orders = orders;
        this.shiprocket = shiprocket;
    }

    WebOrder ship(String number) {
        var order = orders.shopOrder(number);
        if (order.status() != WebOrder.Status.PLACED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only an order that is still to ship can be sent with Shiprocket");
        }
        var shipment = orders.shipment(number);
        long shipmentId;
        if (shipment.shipmentId() == null) {
            var created = shiprocket.createOrder(order);
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
}

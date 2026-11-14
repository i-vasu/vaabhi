package com.vaabhi.checkout;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Shiprocket calls online orders need: sign in (the token lasts 10 days and is kept in memory), create the
 * order, assign a courier (AWB), schedule the pickup, make the label, track, cancel, and check whether a pincode
 * can be served.
 */
@Component
class ShiprocketClient {

    private static final Logger log = LoggerFactory.getLogger(ShiprocketClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter ORDER_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Kolkata"));
    private static final Duration TOKEN_LIFETIME = Duration.ofDays(9);

    private final ShiprocketProperties properties;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private @Nullable String token;
    private Instant tokenExpires = Instant.EPOCH;

    ShiprocketClient(ShiprocketProperties properties) {
        this.properties = properties;
    }

    boolean configured() {
        return properties.configured();
    }

    record CreatedOrder(long orderId, long shipmentId) {}

    record Awb(String awbCode, @Nullable String courierName) {}

    /** The parcel weight for one piece when its product has none set. */
    int defaultWeightGrams() {
        return properties.weightKg().movePointRight(3).intValue();
    }

    /**
     * Creates the order in Shiprocket (our order number is its order id) for the pickup address in settings.
     *
     * @param weightKg the whole parcel
     */
    CreatedOrder createOrder(WebOrder order, BigDecimal weightKg, String invoiceNumber) {
        var phone = order.contactPhone().replaceFirst("^\\+91", "");
        var name = order.contactName().trim();
        var space = name.indexOf(' ');
        List<Map<String, Object>> items = new ArrayList<>();
        for (var line : order.lines()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", line.addonName() == null ? line.name() : line.name() + " + " + line.addonName());
            item.put("sku", line.addonName() == null ? line.sku() : line.sku() + "+" + line.addonName());
            item.put("units", line.quantity());
            item.put("selling_price", line.unitRupees().add(line.addonRupees()));
            item.put("discount", 0);
            item.put("tax", 0);
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", order.number());
        body.put("order_date", ORDER_DATE.format(order.createdAt()));
        body.put("pickup_location", properties.pickupLocation());
        body.put("billing_customer_name", space < 0 ? name : name.substring(0, space));
        body.put("billing_last_name", space < 0 ? "" : name.substring(space + 1).trim());
        body.put(
                "billing_address",
                order.addressLine1() + (order.addressLine2() == null ? "" : ", " + order.addressLine2()));
        body.put("billing_city", order.city());
        body.put("billing_pincode", order.pincode());
        body.put("billing_state", order.state());
        body.put("billing_country", "India");
        body.put("billing_email", order.contactEmail() == null ? "" : order.contactEmail());
        body.put("billing_phone", phone);
        body.put("shipping_is_billing", true);
        body.put("order_items", items);
        body.put("payment_method", order.paymentMethod() == WebOrder.PaymentMethod.COD ? "COD" : "Prepaid");
        body.put("shipping_charges", order.shippingRupees());
        body.put("sub_total", order.itemsRupees());
        body.put("length", properties.lengthCm());
        body.put("breadth", properties.breadthCm());
        body.put("height", properties.heightCm());
        body.put("weight", weightKg);
        body.put("comment", "Invoice " + invoiceNumber);
        var response = call("POST", "/v1/external/orders/create/adhoc", JSON.writeValueAsString(body), order.number());
        var orderId = response.path("order_id").asLong(0);
        var shipmentId = response.path("shipment_id").asLong(0);
        if (orderId == 0 || shipmentId == 0) {
            log.warn(
                    "Shiprocket order for {} was not created: {}",
                    LogSafe.clean(order.number()),
                    LogSafe.clean(response));
            throw unavailable("Shiprocket did not create the order: " + message(response));
        }
        return new CreatedOrder(orderId, shipmentId);
    }

    /**
     * A return pickup from the customer back to the shop's pickup address (its details are read from Shiprocket).
     *
     * @param returnNumber Shiprocket's order id for the return, e.g. WEB-1001-R1
     * @param quantities how many of each order line (by position) come back
     */
    CreatedOrder createReturn(
            WebOrder order, String returnNumber, Map<Integer, Integer> quantities, BigDecimal weightKg) {
        var shop = warehouse(returnNumber);
        var phone = order.contactPhone().replaceFirst("^\\+91", "");
        List<Map<String, Object>> items = new ArrayList<>();
        var subTotal = BigDecimal.ZERO;
        for (var line : order.lines()) {
            var units = quantities.getOrDefault(line.position(), 0);
            if (units == 0) {
                continue;
            }
            var price = line.unitRupees().add(line.addonRupees());
            subTotal = subTotal.add(price.multiply(BigDecimal.valueOf(units)));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", line.addonName() == null ? line.name() : line.name() + " + " + line.addonName());
            item.put("sku", line.addonName() == null ? line.sku() : line.sku() + "+" + line.addonName());
            item.put("units", units);
            item.put("selling_price", price);
            item.put("discount", 0);
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", returnNumber);
        body.put("order_date", ORDER_DATE.format(Instant.now()));
        body.put("pickup_customer_name", order.contactName().trim());
        body.put(
                "pickup_address",
                order.addressLine1() + (order.addressLine2() == null ? "" : ", " + order.addressLine2()));
        body.put("pickup_city", order.city());
        body.put("pickup_state", order.state());
        body.put("pickup_country", "India");
        body.put("pickup_pincode", order.pincode());
        body.put("pickup_email", order.contactEmail() == null ? "" : order.contactEmail());
        body.put("pickup_phone", phone);
        body.put("shipping_customer_name", text(shop.path("name")) == null ? "Vaabhi" : text(shop.path("name")));
        body.put("shipping_address", textOrEmpty(shop.path("address")));
        body.put("shipping_address_2", textOrEmpty(shop.path("address_2")));
        body.put("shipping_city", textOrEmpty(shop.path("city")));
        body.put("shipping_state", textOrEmpty(shop.path("state")));
        body.put("shipping_country", "India");
        body.put("shipping_pincode", textOrEmpty(shop.path("pin_code")));
        body.put("shipping_email", textOrEmpty(shop.path("email")));
        body.put("shipping_phone", textOrEmpty(shop.path("phone")));
        body.put("order_items", items);
        body.put("payment_method", "Prepaid");
        body.put("sub_total", subTotal);
        body.put("length", properties.lengthCm());
        body.put("breadth", properties.breadthCm());
        body.put("height", properties.heightCm());
        body.put("weight", weightKg);
        var response = call("POST", "/v1/external/orders/create/return", JSON.writeValueAsString(body), returnNumber);
        var orderId = response.path("order_id").asLong(0);
        var shipmentId = response.path("shipment_id").asLong(0);
        if (orderId == 0 || shipmentId == 0) {
            log.warn(
                    "Shiprocket return for {} was not created: {}",
                    LogSafe.clean(returnNumber),
                    LogSafe.clean(response));
            throw unavailable("Shiprocket did not create the return: " + message(response));
        }
        return new CreatedOrder(orderId, shipmentId);
    }

    /** The pickup address named in settings, as set up in Shiprocket (where returns are sent back to). */
    private JsonNode warehouse(String number) {
        var addresses = call("GET", "/v1/external/settings/company/pickup", null, number)
                .path("data")
                .path("shipping_address");
        for (var address : addresses) {
            if (properties.pickupLocation().equalsIgnoreCase(textOrEmpty(address.path("pickup_location")))) {
                return address;
            }
        }
        throw unavailable("Shiprocket has no pickup address named \"" + properties.pickupLocation()
                + "\". Check SHIPROCKET_PICKUP_LOCATION.");
    }

    /** Assigns the courier Shiprocket recommends (per the account's courier priority) and returns the AWB. */
    Awb assignAwb(long shipmentId, String number) {
        return assignAwb(shipmentId, number, false);
    }

    /** As {@link #assignAwb(long, String)}; {@code isReturn} for a return pickup's shipment. */
    Awb assignAwb(long shipmentId, String number, boolean isReturn) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shipment_id", shipmentId);
        if (isReturn) {
            body.put("is_return", 1);
        }
        var response = call("POST", "/v1/external/courier/assign/awb", JSON.writeValueAsString(body), number);
        var data = response.path("response").path("data");
        var awb = text(data.path("awb_code"));
        if (awb == null) {
            log.warn("Shiprocket gave no AWB for {}: {}", LogSafe.clean(number), LogSafe.clean(response));
            throw unavailable("Shiprocket could not assign a courier: " + message(response));
        }
        return new Awb(awb, text(data.path("courier_name")));
    }

    /** Asks the courier to pick the parcel up. Failure is logged only: the pickup can be requested in Shiprocket. */
    void requestPickup(long shipmentId, String number) {
        try {
            call(
                    "POST",
                    "/v1/external/courier/generate/pickup",
                    JSON.writeValueAsString(Map.of("shipment_id", List.of(shipmentId))),
                    number);
        } catch (ResponseStatusException e) {
            log.warn("Shiprocket pickup for {} was not scheduled: {}", LogSafe.clean(number), e.getReason());
        }
    }

    /** The shipping label (PDF) to print and stick on the parcel; null if Shiprocket could not make it yet. */
    @Nullable
    String label(long shipmentId, String number) {
        try {
            var response = call(
                    "POST",
                    "/v1/external/courier/generate/label",
                    JSON.writeValueAsString(Map.of("shipment_id", List.of(shipmentId))),
                    number);
            return text(response.path("label_url"));
        } catch (ResponseStatusException e) {
            log.warn("Shiprocket label for {} was not made: {}", LogSafe.clean(number), e.getReason());
            return null;
        }
    }

    /** The courier's latest status for an AWB (e.g. "IN TRANSIT", "DELIVERED"), or null when there is none yet. */
    @Nullable
    String trackingStatus(String awb, String number) {
        var response = call("GET", "/v1/external/courier/track/awb/" + encode(awb), null, number);
        var tracking = response.path("tracking_data");
        var track = tracking.path("shipment_track");
        if (track.isArray() && !track.isEmpty()) {
            var status = text(track.get(0).path("current_status"));
            if (status != null) {
                return status;
            }
        }
        var activities = tracking.path("shipment_track_activities");
        return activities.isArray() && !activities.isEmpty()
                ? text(activities.get(0).path("activity"))
                : null;
    }

    /** Cancels the order in Shiprocket (the courier will not pick it up). */
    void cancel(long shiprocketOrderId, String number) {
        call(
                "POST",
                "/v1/external/orders/cancel",
                JSON.writeValueAsString(Map.of("ids", List.of(shiprocketOrderId))),
                number);
    }

    enum Serviceable {
        YES,
        NO,
        /** Shiprocket could not be asked (not set up, no pickup pincode, or it did not answer). */
        UNKNOWN
    }

    /** Whether any courier delivers to {@code pincode} from the pickup address (with cash on delivery if asked). */
    Serviceable serviceable(String pincode, boolean cod) {
        if (!configured()
                || properties.pickupPincode() == null
                || properties.pickupPincode().isBlank()) {
            return Serviceable.UNKNOWN;
        }
        var path = "/v1/external/courier/serviceability/?pickup_postcode=" + encode(properties.pickupPincode())
                + "&delivery_postcode=" + encode(pincode) + "&weight="
                + properties.weightKg().toPlainString()
                + "&cod=" + (cod ? 1 : 0);
        try {
            var response = send("GET", path, null);
            if (response.statusCode() == 404) {
                return Serviceable.NO;
            }
            if (response.statusCode() / 100 != 2) {
                log.warn("Shiprocket serviceability for {} answered {}", LogSafe.clean(pincode), response.statusCode());
                return Serviceable.UNKNOWN;
            }
            var couriers = JSON.readTree(response.body()).path("data").path("available_courier_companies");
            return couriers.isArray() && !couriers.isEmpty() ? Serviceable.YES : Serviceable.NO;
        } catch (RuntimeException e) {
            log.warn("Shiprocket serviceability for {} failed", LogSafe.clean(pincode), e);
            return Serviceable.UNKNOWN;
        }
    }

    /** Shiprocket sends the token set on the webhook back in the x-api-key header. */
    boolean webhookTokenValid(String given) {
        var expected = properties.webhookToken();
        if (expected == null || expected.isBlank()) {
            log.warn("Shiprocket webhook received but SHIPROCKET_WEBHOOK_TOKEN is not set; ignoring it");
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    // --- HTTP -------------------------------------------------------------------------------------------------

    /** Calls the API with the token (signing in again once if it expired); any failure becomes a 503. */
    private JsonNode call(String method, String path, @Nullable String body, String number) {
        requireConfigured();
        var response = send(method, path, body);
        if (response.statusCode() == 401) {
            clearToken();
            response = send(method, path, body);
        }
        var json = response.body() == null || response.body().isBlank()
                ? JSON.createObjectNode()
                : JSON.readTree(response.body());
        if (response.statusCode() / 100 != 2) {
            log.warn(
                    "Shiprocket {} for {} failed: {} {}",
                    LogSafe.clean(path),
                    LogSafe.clean(number),
                    response.statusCode(),
                    LogSafe.clean(response.body()));
            throw unavailable("Shiprocket refused the request: " + message(json));
        }
        return json;
    }

    private HttpResponse<String> send(String method, String path, @Nullable String body) {
        var request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .method(
                        method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return exchange(request);
    }

    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpires)) {
            return token;
        }
        var body = JSON.writeValueAsString(Map.of("email", properties.email(), "password", properties.password()));
        var request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/v1/external/auth/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = exchange(request);
        var signedIn = response.statusCode() / 100 == 2
                ? text(JSON.readTree(response.body()).path("token"))
                : null;
        if (signedIn == null) {
            log.warn("Shiprocket sign-in failed: {} {}", response.statusCode(), LogSafe.clean(response.body()));
            throw unavailable("Shiprocket sign-in failed. Check SHIPROCKET_EMAIL and SHIPROCKET_PASSWORD.");
        }
        token = signedIn;
        tokenExpires = Instant.now().plus(TOKEN_LIFETIME);
        return signedIn;
    }

    private synchronized void clearToken() {
        token = null;
    }

    private HttpResponse<String> exchange(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Shiprocket could not be reached. Please try again.");
        } catch (java.io.IOException e) {
            log.warn("Shiprocket {} failed", LogSafe.clean(request.uri().getPath()), e);
            throw unavailable("Shiprocket could not be reached. Please try again.");
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw unavailable("Shiprocket is not set up yet");
        }
    }

    private static String message(JsonNode response) {
        var message = text(response.path("message"));
        if (message == null) {
            message = text(response.path("response").path("data").path("awb_assign_error"));
        }
        return message == null ? "no reason given" : message;
    }

    static @Nullable String text(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return null;
        }
        var value = node.asString();
        return value.isBlank() ? null : value;
    }

    private static String textOrEmpty(JsonNode node) {
        var value = text(node);
        return value == null ? "" : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

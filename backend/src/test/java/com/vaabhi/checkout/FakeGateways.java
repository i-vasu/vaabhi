package com.vaabhi.checkout;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay and Shiprocket on localhost, answering like the real APIs do and remembering every request, so the
 * checkout flows run end to end without the internet.
 */
final class FakeGateways implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Call(String method, String path, String query, String body, Map<String, String> headers) {
        JsonNode json() {
            return JSON.readTree(body.isBlank() ? "{}" : body);
        }
    }

    final List<Call> calls = new CopyOnWriteArrayList<>();
    /** Payments Razorpay reports per order id (for "fetch the order's payments"). */
    final Map<String, String> paymentsByOrder = new ConcurrentHashMap<>();
    /** Pincodes no courier serves. */
    final List<String> unserviceable = new CopyOnWriteArrayList<>(List.of("999999"));
    /** Refunds already on a payment, when a test wants the GET refunds answer to report them (a JSON array). */
    volatile String refundsList;
    /** When true, POST refund is refused with the message a test checks for. */
    volatile boolean refuseRefunds;

    private final AtomicInteger ids = new AtomicInteger(100);
    private final HttpServer server;

    FakeGateways() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Call> calls(String method, String pathPrefix) {
        return calls.stream()
                .filter(c -> c.method().equals(method) && c.path().startsWith(pathPrefix))
                .toList();
    }

    Call last(String method, String pathPrefix) {
        var matching = calls(method, pathPrefix);
        if (matching.isEmpty()) {
            throw new AssertionError("No " + method + " " + pathPrefix + " call; calls were " + calls);
        }
        return matching.getLast();
    }

    private void handle(HttpExchange exchange) throws IOException {
        var method = exchange.getRequestMethod();
        var path = exchange.getRequestURI().getPath();
        var query = exchange.getRequestURI().getQuery() == null
                ? ""
                : exchange.getRequestURI().getQuery();
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(java.util.Locale.ROOT), v.getFirst()));
        calls.add(new Call(method, path, query, body, headers));
        var answer = answer(method, path, query);
        var bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answer.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record Answer(int status, String body) {}

    private Answer answer(String method, String path, String query) {
        int id = ids.incrementAndGet();
        // Razorpay
        if (method.equals("POST") && path.equals("/v1/orders")) {
            return ok("{\"id\":\"order_T" + id + "\",\"status\":\"created\"}");
        }
        if (method.equals("GET") && path.startsWith("/v1/orders/") && path.endsWith("/payments")) {
            var orderId = path.substring("/v1/orders/".length(), path.length() - "/payments".length());
            return ok("{\"items\":[" + paymentsByOrder.getOrDefault(orderId, "") + "]}");
        }
        if (method.equals("POST") && path.matches("/v1/payments/[^/]+/capture")) {
            return ok("{\"status\":\"captured\"}");
        }
        if (method.equals("GET") && path.matches("/v1/payments/[^/]+/refunds")) {
            // No earlier refund on the payment, so a cancel goes on to refund it (unless a test sets one).
            var refunds = refundsList;
            return ok("{\"items\":" + (refunds == null ? "[]" : refunds) + "}");
        }
        if (method.equals("POST") && path.matches("/v1/payments/[^/]+/refund")) {
            if (refuseRefunds) {
                return new Answer(400, "{\"error\":{\"description\":\"The amount is invalid.\"}}");
            }
            return ok("{\"id\":\"rfnd_T" + id + "\",\"status\":\"pending\"}");
        }
        // Refunds already made on a payment: the refund lock looks before refunding. None in these tests.
        if (method.equals("GET") && path.matches("/v1/payments/[^/]+/refunds")) {
            return ok("{\"entity\":\"collection\",\"count\":0,\"items\":[]}");
        }
        // Shiprocket
        if (path.equals("/v1/external/auth/login")) {
            return ok("{\"token\":\"sr-token\"}");
        }
        if (path.startsWith("/v1/external/courier/serviceability")) {
            var pincode = query.replaceAll(".*delivery_postcode=(\\d+).*", "$1");
            return unserviceable.contains(pincode)
                    ? new Answer(404, "{\"status\":404,\"message\":\"No courier serviceable\"}")
                    : ok(
                            "{\"status\":200,\"data\":{\"available_courier_companies\":[{\"courier_name\":\"Delhivery\"}]}}");
        }
        if (path.equals("/v1/external/orders/create/adhoc") || path.equals("/v1/external/orders/create/return")) {
            return ok("{\"order_id\":" + id + ",\"shipment_id\":" + (id + 5000) + ",\"status\":\"NEW\"}");
        }
        if (path.equals("/v1/external/courier/assign/awb")) {
            return ok("{\"awb_assign_status\":1,\"response\":{\"data\":{\"awb_code\":\"AWB" + id
                    + "\",\"courier_name\":\"Delhivery Surface\"}}}");
        }
        if (path.equals("/v1/external/courier/generate/pickup")) {
            return ok("{\"pickup_status\":1}");
        }
        if (path.equals("/v1/external/courier/generate/label")) {
            return ok("{\"label_created\":1,\"label_url\":\"https://labels.test/" + id + ".pdf\"}");
        }
        if (path.startsWith("/v1/external/courier/track/awb/")) {
            return ok(
                    "{\"tracking_data\":{\"track_status\":1,\"shipment_track\":[{\"current_status\":\"IN TRANSIT\"}]}}");
        }
        if (path.equals("/v1/external/orders/cancel")) {
            return ok("{\"status\":200}");
        }
        if (path.equals("/v1/external/settings/company/pickup")) {
            return ok("{\"data\":{\"shipping_address\":[{\"pickup_location\":\"Primary\",\"name\":\"Vaabhi\","
                    + "\"address\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\","
                    + "\"pin_code\":\"560001\",\"email\":\"shop@vaabhi.test\",\"phone\":\"9800000000\"}]}}");
        }
        return new Answer(404, "{\"message\":\"no fake for " + method + " " + path + "\"}");
    }

    private static Answer ok(String body) {
        return new Answer(200, body);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

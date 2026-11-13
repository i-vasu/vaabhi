package com.vaabhi.checkout;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Razorpay calls checkout needs: create an order, fetch and capture its payments, refund a payment (amounts in
 * paise, as Razorpay requires) and check the HMAC-SHA256 signatures on payments and webhooks.
 */
@Component
class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RazorpayProperties properties;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    RazorpayClient(RazorpayProperties properties) {
        this.properties = properties;
    }

    String keyId() {
        requireConfigured();
        return properties.keyId();
    }

    /** Creates a Razorpay order for {@code amountRupees} and returns its id (order_…). */
    String createOrder(BigDecimal amountRupees, String receipt) {
        requireConfigured();
        var body = JSON.writeValueAsString(Map.of(
                "amount",
                paise(amountRupees),
                "currency",
                "INR",
                "receipt",
                receipt,
                "notes",
                Map.of("order", receipt)));
        var credentials = Base64.getEncoder()
                .encodeToString((properties.keyId() + ":" + properties.keySecret()).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/v1/orders"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn(
                        "Razorpay order for {} failed: {} {}",
                        LogSafe.clean(receipt),
                        response.statusCode(),
                        LogSafe.clean(response.body()));
                throw unavailable("Online payment is not available right now. Please try again.");
            }
            return JSON.readTree(response.body()).get("id").asString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Online payment is not available right now. Please try again.");
        } catch (java.io.IOException e) {
            log.warn("Razorpay order for {} failed", LogSafe.clean(receipt), e);
            throw unavailable("Online payment is not available right now. Please try again.");
        }
    }

    record Refund(String id, String status) {}

    /** Refunds {@code amountRupees} of a captured payment and returns the refund (status pending or processed). */
    Refund refund(String paymentId, BigDecimal amountRupees, String receipt) {
        requireConfigured();
        var body = JSON.writeValueAsString(
                Map.of("amount", paise(amountRupees), "receipt", receipt, "notes", Map.of("order", receipt)));
        var refund = JSON.readTree(call("POST", "/v1/payments/" + paymentId + "/refund", body, receipt));
        return new Refund(refund.get("id").asString(), refund.path("status").asString("pending"));
    }

    /**
     * A refund of exactly {@code amountRupees} already made on the payment and not failed, if there is one. Checked
     * before refunding again, so a retry after a timeout (Razorpay refunded, the shop never heard) cannot refund twice.
     */
    Optional<Refund> existingRefund(String paymentId, BigDecimal amountRupees, String receipt) {
        requireConfigured();
        var items = JSON.readTree(call("GET", "/v1/payments/" + paymentId + "/refunds", null, receipt))
                .path("items");
        var paise = paise(amountRupees);
        for (var item : items) {
            var status = item.path("status").asString("");
            if (!status.equals("failed") && item.path("amount").asLong(0) == paise) {
                return Optional.of(new Refund(item.path("id").asString(""), status));
            }
        }
        return Optional.empty();
    }

    record Payment(String id, String status, long amountPaise) {}

    /** The payment attempts on a Razorpay order (status created, authorized, captured, refunded or failed). */
    List<Payment> orderPayments(String razorpayOrderId, String receipt) {
        requireConfigured();
        var items = JSON.readTree(call("GET", "/v1/orders/" + razorpayOrderId + "/payments", null, receipt))
                .path("items");
        List<Payment> payments = new ArrayList<>();
        for (var item : items) {
            payments.add(new Payment(
                    item.path("id").asString(""),
                    item.path("status").asString(""),
                    item.path("amount").asLong(0)));
        }
        return payments;
    }

    /** A page of settlement batches (100 per page, in paise) between the two epoch-second instants. */
    List<RazorpaySettlementData> settlements(long fromEpoch, long toEpoch, int skip) {
        requireConfigured();
        var items = JSON.readTree(call(
                        "GET",
                        "/v1/settlements?from=" + fromEpoch + "&to=" + toEpoch + "&count=100&skip=" + skip,
                        null,
                        "settlements"))
                .path("items");
        List<RazorpaySettlementData> settlements = new ArrayList<>();
        for (var item : items) {
            var utr = item.path("utr");
            settlements.add(new RazorpaySettlementData(
                    item.path("id").asString(""),
                    utr.isNull() || utr.isMissingNode() ? null : utr.asString(),
                    item.path("amount").asLong(0),
                    item.path("fees").asLong(0),
                    item.path("tax").asLong(0),
                    item.path("status").asString(""),
                    item.path("created_at").asLong(0)));
        }
        return settlements;
    }

    /** Captures an authorized payment (needed only when auto-capture is off in the Razorpay dashboard). */
    void capture(String paymentId, long amountPaise, String receipt) {
        requireConfigured();
        var body = JSON.writeValueAsString(Map.of("amount", amountPaise, "currency", "INR"));
        call("POST", "/v1/payments/" + paymentId + "/capture", body, receipt);
    }

    boolean configured() {
        return properties.configured();
    }

    /** Calls the API with the keys; any failure becomes a 503 the shop can retry. */
    private String call(String method, String path, @Nullable String body, String receipt) {
        var credentials = Base64.getEncoder()
                .encodeToString((properties.keyId() + ":" + properties.keySecret()).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .method(
                        method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn(
                        "Razorpay {} for {} failed: {} {}",
                        LogSafe.clean(path),
                        LogSafe.clean(receipt),
                        response.statusCode(),
                        LogSafe.clean(response.body()));
                var reason = errorDescription(response.body());
                throw unavailable("Razorpay refused the request" + (reason == null ? "" : ": " + reason)
                        + ". Please try again or check the Razorpay dashboard.");
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Razorpay could not be reached. Please try again.");
        } catch (java.io.IOException e) {
            log.warn("Razorpay {} for {} failed", LogSafe.clean(path), LogSafe.clean(receipt), e);
            throw unavailable("Razorpay could not be reached. Please try again.");
        }
    }

    /** Razorpay Checkout's handler signature: HMAC-SHA256 of "order_id|payment_id" with the key secret. */
    boolean paymentSignatureValid(String razorpayOrderId, String razorpayPaymentId, String signature) {
        requireConfigured();
        return matches(hmac(properties.keySecret(), razorpayOrderId + "|" + razorpayPaymentId), signature);
    }

    /** X-Razorpay-Signature on webhooks: HMAC-SHA256 of the raw body with the webhook secret. */
    boolean webhookSignatureValid(String body, String signature) {
        var secret = properties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Razorpay webhook received but RAZORPAY_WEBHOOK_SECRET is not set; ignoring it");
            return false;
        }
        return matches(hmac(secret, body), signature);
    }

    static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).longValueExact();
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw unavailable("Online payment is not set up yet");
        }
    }

    private static boolean matches(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    static String hmac(String secret, String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** Razorpay's own words for a refused request ({"error":{"description":…}}), cut short; null when absent. */
    static @Nullable String errorDescription(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            var description = JSON.readTree(body)
                    .path("error")
                    .path("description")
                    .asString("")
                    .strip();
            if (description.isEmpty()) {
                return null;
            }
            description = description.endsWith(".") ? description.substring(0, description.length() - 1) : description;
            return description.length() <= 120 ? description : description.substring(0, 120);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two Razorpay calls checkout needs: create an order (amount in paise, as Razorpay requires) and check the
 * HMAC-SHA256 signatures on payments and webhooks.
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
                log.warn("Razorpay order for {} failed: {} {}", receipt, response.statusCode(), response.body());
                throw unavailable("Online payment is not available right now. Please try again.");
            }
            return JSON.readTree(response.body()).get("id").asString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Online payment is not available right now. Please try again.");
        } catch (java.io.IOException e) {
            log.warn("Razorpay order for {} failed", receipt, e);
            throw unavailable("Online payment is not available right now. Please try again.");
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

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

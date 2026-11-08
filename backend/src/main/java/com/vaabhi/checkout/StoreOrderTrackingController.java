package com.vaabhi.checkout;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Guest order tracking: the order number plus the phone it was placed with. A wrong number and a wrong phone get
 * the same answer, and an order number stops answering for a while after too many wrong phones.
 */
@RestController
@RequestMapping("/api/v1/store/orders")
class StoreOrderTrackingController {

    private static final int MAX_WRONG_TRIES = 10;
    private static final String NOT_FOUND = "We couldn't find an order with that number and mobile number";
    private static final String TOO_MANY_TRIES = "Too many tries. Please wait a few minutes.";

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final CheckoutService orders;
    private final ShiprocketShipping shipping;
    private final Cache<String, AtomicInteger> wrongTries = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();
    /** Orders we asked Shiprocket about lately. */
    private final Cache<String, Boolean> liveAsked = Caffeine.newBuilder()
            .expireAfterWrite(STALE_AFTER)
            .maximumSize(10_000)
            .build();

    StoreOrderTrackingController(CheckoutService orders, ShiprocketShipping shipping) {
        this.orders = orders;
        this.shipping = shipping;
    }

    @PostMapping("/track")
    TrackedOrder track(@Valid @RequestBody TrackOrderRequest request) {
        var number = orderNumber(request.number());
        var tries = wrongTries.getIfPresent(number);
        if (tries != null && tries.get() >= MAX_WRONG_TRIES) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_TRIES);
        }
        var phone = lastTenDigits(request.phone());
        var order = orders.findByNumber(number)
                .filter(o ->
                        phone.length() == 10 && lastTenDigits(o.contactPhone()).equals(phone))
                .orElseThrow(() -> {
                    wrongTries.get(number, k -> new AtomicInteger()).incrementAndGet();
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
                });
        return TrackedOrder.of(withLiveTracking(order));
    }

    /**
     * Asks Shiprocket for the courier's latest status when ours is older than {@link #STALE_AFTER} (the webhook may
     * not be set up, or missed an update). At most once per order per {@link #STALE_AFTER}, even when Shiprocket has
     * nothing new, so repeated lookups stay within Shiprocket's rate limits.
     */
    private WebOrder withLiveTracking(WebOrder order) {
        if (order.status() != WebOrder.Status.SHIPPED) {
            return order;
        }
        if (liveAsked.asMap().putIfAbsent(order.number(), Boolean.TRUE) != null) {
            return order;
        }
        return shipping.withFreshTracking(order, STALE_AFTER, OffsetDateTime.now());
    }

    /** "web-1042", "1042" and " WEB-1042 " all mean WEB-1042. */
    private static String orderNumber(String raw) {
        var n = raw.strip().toUpperCase(Locale.ROOT).replace(" ", "");
        return n.startsWith("WEB-") ? n : "WEB-" + n;
    }

    private static String lastTenDigits(String phone) {
        var digits = phone.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }
}

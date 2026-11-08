package com.vaabhi.checkout;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shiprocket's tracking updates (Shiprocket → Settings → API → Webhooks, URL
 * https://api.vaabhi.com/api/v1/webhooks/delivery-updates, with the token from SHIPROCKET_WEBHOOK_TOKEN).
 * Shiprocket refuses URLs containing "shiprocket", "kartrocket", "sr" or "kr", hence the neutral path.
 * Public: the token is the authentication.
 */
@RestController
class ShiprocketWebhookController {

    private final ShiprocketShipping shipping;

    ShiprocketWebhookController(ShiprocketShipping shipping) {
        this.shipping = shipping;
    }

    @PostMapping("/api/v1/webhooks/delivery-updates")
    void deliveryUpdate(@RequestBody String body, @RequestHeader(name = "x-api-key", defaultValue = "") String token) {
        shipping.webhook(body, token);
    }
}

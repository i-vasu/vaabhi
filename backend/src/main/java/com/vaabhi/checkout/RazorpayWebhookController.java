package com.vaabhi.checkout;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay's server-to-server events (dashboard → Webhooks → https://api.vaabhi.com/api/v1/webhooks/razorpay, with
 * events payment.captured, payment.failed and order.paid). Public: the signature is the authentication.
 */
@RestController
class RazorpayWebhookController {

    private final CheckoutService service;

    RazorpayWebhookController(CheckoutService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/webhooks/razorpay")
    void razorpayWebhook(
            @RequestBody String body,
            @RequestHeader(name = "X-Razorpay-Signature", defaultValue = "") String signature) {
        service.webhook(body, signature);
    }
}

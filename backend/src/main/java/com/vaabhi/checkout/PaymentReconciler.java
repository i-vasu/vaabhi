package com.vaabhi.checkout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Every 10 minutes, checks online orders that have waited over 15 minutes for payment with Razorpay, in case the
 * browser never came back and the webhook was missed (or is not set up). Paid orders are placed, authorized
 * payments captured, and orders still unpaid after 2 hours marked FAILED.
 */
@Component
class PaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);
    private static final int WAIT_MINUTES = 15;
    private static final int GIVE_UP_MINUTES = 120;

    private final CheckoutService orders;
    private final RazorpayClient razorpay;

    PaymentReconciler(CheckoutService orders, RazorpayClient razorpay) {
        this.orders = orders;
        this.razorpay = razorpay;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT10M")
    void reconcile() {
        if (!razorpay.configured()) {
            return;
        }
        for (var number : orders.waitingForPayment(WAIT_MINUTES, 50)) {
            try {
                orders.reconcilePayment(number, GIVE_UP_MINUTES);
            } catch (ResponseStatusException e) {
                log.warn("Could not check the payment for {}: {}", LogSafe.clean(number), e.getReason());
            }
        }
    }
}

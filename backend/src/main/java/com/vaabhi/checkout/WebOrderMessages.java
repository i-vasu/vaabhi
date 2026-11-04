package com.vaabhi.checkout;

import com.vaabhi.shared.sms.SmsSender;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * SMS to the customer when an online order is placed, shipped, delivered or cancelled. Sent after the change is
 * saved; a failed SMS is logged and never undoes the order change. Templates (MSG91, DLT-approved) are named
 * web_order_placed, web_order_shipped, web_order_delivered and web_order_cancelled; their variables are order,
 * amount, payment, courier, tracking and refund.
 */
@Component
class WebOrderMessages {

    private static final Logger log = LoggerFactory.getLogger(WebOrderMessages.class);

    private final SmsSender sms;

    WebOrderMessages(SmsSender sms) {
        this.sms = sms;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WebOrderEvent event) {
        var variables = new LinkedHashMap<String, String>();
        variables.put("order", event.number());
        variables.put("amount", event.totalRupees().toPlainString());
        variables.put(
                "payment", event.paymentMethod() == WebOrder.PaymentMethod.COD ? "Cash on delivery" : "Paid online");
        variables.put("courier", event.courier() == null ? "our delivery partner" : event.courier());
        variables.put("tracking", event.trackingNumber() == null ? "-" : event.trackingNumber());
        variables.put(
                "refund",
                event.refundedRupees() == null ? "0" : event.refundedRupees().toPlainString());
        var template = "web_order_" + event.type().name().toLowerCase(java.util.Locale.ROOT);
        try {
            sms.send(event.phone(), template, variables);
        } catch (RuntimeException e) {
            log.warn("Order SMS {} for {} was not sent: {}", template, LogSafe.clean(event.number()), e.getMessage());
        }
    }
}

package com.vaabhi.checkout;

import com.vaabhi.shared.whatsapp.WhatsAppSender;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * WhatsApp to the customer when an online order is placed, shipped, delivered or cancelled. Sent after the change is
 * saved; a failed message is logged and never undoes the order change. Templates are named web_order_placed,
 * web_order_shipped, web_order_delivered and web_order_cancelled (each needs a Meta-approved template; a message
 * without one is not sent). Their variables are order, amount, payment, courier, tracking and refund.
 */
@Component
class WebOrderMessages {

    private static final Logger log = LoggerFactory.getLogger(WebOrderMessages.class);

    private final WhatsAppSender whatsApp;

    WebOrderMessages(WhatsAppSender whatsApp) {
        this.whatsApp = whatsApp;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WebOrderEvent event) {
        var variables = variables(event);
        var template = "web_order_" + event.type().name().toLowerCase(Locale.ROOT);
        if (whatsApp.configured(template)) {
            try {
                whatsApp.send(event.phone(), template, variables);
            } catch (RuntimeException e) {
                log.warn(
                        "Order WhatsApp {} for {} was not sent: {}",
                        template,
                        LogSafe.clean(event.number()),
                        e.getMessage());
            }
        }
    }

    static Map<String, String> variables(WebOrderEvent event) {
        var variables = new LinkedHashMap<String, String>();
        variables.put("order", event.number());
        variables.put("amount", rupees(event.totalRupees()));
        variables.put(
                "payment", event.paymentMethod() == WebOrder.PaymentMethod.COD ? "Cash on delivery" : "Paid online");
        variables.put("courier", event.courier() == null ? "our delivery partner" : event.courier());
        variables.put("tracking", event.trackingNumber() == null ? "-" : event.trackingNumber());
        variables.put("refund", rupees(event.refundedRupees()));
        return variables;
    }

    /** 24900.00 → "24,900"; 1250.50 → "1,250.50" (Indian digit grouping, paise only when there are some). */
    static String rupees(@Nullable BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        // Java's number formats group in threes only, so the Indian 2-digit groups are built by hand.
        var rounded = amount.setScale(2, RoundingMode.HALF_UP);
        var digits = rounded.abs().toBigInteger().toString();
        var grouped = new StringBuilder();
        int head = digits.length() - 3;
        if (head > 0) {
            for (int i = 0; i < head; i++) {
                grouped.append(digits.charAt(i));
                if ((head - i - 1) % 2 == 0) {
                    grouped.append(',');
                }
            }
            grouped.append(digits, head, digits.length());
        } else {
            grouped.append(digits);
        }
        var paise = rounded.remainder(BigDecimal.ONE).abs().movePointRight(2).intValue();
        return (rounded.signum() < 0 ? "-" : "") + grouped + (paise == 0 ? "" : String.format(".%02d", paise));
    }
}

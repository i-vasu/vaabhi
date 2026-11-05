package com.vaabhi.checkout;

import com.vaabhi.shared.email.EmailMessage;
import com.vaabhi.shared.email.EmailSender;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails the customer when an online order is placed, shipped, delivered or cancelled, and at each step of a return
 * (approved, pickup booked, rejected, refunded). Only orders with an email address get one. Sent after the change
 * is saved; a failed email is logged and never undoes the order change (like the order SMS).
 */
@Component
class OrderEmails {

    private static final Logger log = LoggerFactory.getLogger(OrderEmails.class);

    private final EmailSender email;
    private final CheckoutService orders;
    private final CheckoutProperties properties;

    OrderEmails(EmailSender email, CheckoutService orders, CheckoutProperties properties) {
        this.email = email;
        this.orders = orders;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WebOrderEvent event) {
        var order = orders.shopOrder(event.number());
        if (hasEmail(order)) {
            send(OrderEmailTemplates.forOrder(event.type(), order, properties.storefrontUrl()), event.number());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WebOrderReturnEvent event) {
        var order = orders.shopOrder(event.orderNumber());
        if (hasEmail(order)) {
            send(
                    OrderEmailTemplates.forReturn(
                            event.type(), order, event.returnNumber(), properties.storefrontUrl()),
                    event.returnNumber());
        }
    }

    private static boolean hasEmail(WebOrder order) {
        return order.contactEmail() != null && !order.contactEmail().isBlank();
    }

    private void send(@Nullable EmailMessage message, String number) {
        if (message == null) {
            return;
        }
        try {
            email.send(message);
        } catch (RuntimeException e) {
            log.warn("Order email for {} was not sent: {}", LogSafe.clean(number), LogSafe.clean(e.getMessage()));
        }
    }
}

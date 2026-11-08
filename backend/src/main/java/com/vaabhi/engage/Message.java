package com.vaabhi.engage;

import com.vaabhi.settings.MessageTemplates;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A WhatsApp or SMS message prepared for (or sent to) a customer.
 *
 * @param link wa.me / sms: link that opens the message ready to send from the shop's phone (status LINK)
 */
public record Message(
        long id,
        MessageTemplates.Channel channel,
        Purpose purpose,
        @Nullable Long clientId,
        @Nullable String clientName,
        @Nullable Long orderId,
        @Nullable String orderNumber,
        String toPhone,
        String body,
        Status status,
        @Nullable String link,
        @Nullable String error,
        @Nullable String createdBy,
        OffsetDateTime createdAt) {

    public enum Purpose {
        ORDER_PLACED,
        ORDER_READY,
        ORDER_DELIVERED,
        PAYMENT,
        REVIEW,
        REWARD,
        CUSTOM
    }

    public enum Status {
        /** A link was made for staff to send from their own phone. */
        LINK,
        SENT,
        FAILED
    }

    /**
     * Prepare a message from the shop's templates (Settings → Messages) for an order or a customer.
     *
     * @param orderId fills {order}, {amount}, {balance} and {date}; needed for the order purposes
     * @param clientId the customer, when there is no order
     * @param code the coupon code for a REWARD message
     * @param body the text of a CUSTOM message
     * @param channel defaults to the channel chosen in the templates
     */
    public record MessageForm(
            @NotNull Purpose purpose,
            @Nullable Long orderId,
            @Nullable Long clientId,
            @Nullable @Size(max = 40) String code,
            @Nullable @Size(max = 2000) String body,
            MessageTemplates.@Nullable Channel channel) {}
}

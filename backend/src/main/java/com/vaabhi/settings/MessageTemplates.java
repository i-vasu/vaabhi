package com.vaabhi.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WhatsApp / SMS wording sent to customers. Placeholders in braces are filled in when sending: {customer}, {shop},
 * {order}, {amount}, {balance}, {date}, {link}, {code}.
 */
public record MessageTemplates(
        @NotNull Channel channel,
        @NotBlank @Size(max = 1000) String orderPlaced,
        @NotBlank @Size(max = 1000) String orderReady,
        @NotBlank @Size(max = 1000) String orderDelivered,
        @NotBlank @Size(max = 1000) String paymentReceived,
        @NotBlank @Size(max = 1000) String reviewRequest,
        @NotBlank @Size(max = 1000) String reward) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)}");

    public enum Channel {
        WHATSAPP,
        SMS
    }

    /** Replaces known placeholders; unknown ones are left as typed so mistakes stay visible. */
    public static String fill(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        var out = new StringBuilder();
        while (matcher.find()) {
            var value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static MessageTemplates defaults() {
        return new MessageTemplates(
                Channel.WHATSAPP,
                "Hi {customer}, thank you for your order {order} at {shop}. Total {amount}, balance {balance}."
                        + " It will be ready by {date}.",
                "Hi {customer}, your order {order} is ready for pickup at {shop}. Balance due: {balance}.",
                "Hi {customer}, your order {order} has been delivered. Thank you for choosing {shop}!",
                "Hi {customer}, we received {amount} for order {order}. Balance: {balance}. - {shop}",
                "Hi {customer}, we hope you love your outfit from {shop}! Could you leave us a review? {link}",
                "Hi {customer}, here is a gift from {shop}: use code {code} on your next order.");
    }
}

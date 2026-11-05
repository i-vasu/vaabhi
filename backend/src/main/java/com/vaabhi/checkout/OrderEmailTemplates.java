package com.vaabhi.checkout;

import com.vaabhi.shared.email.EmailMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

/**
 * The emails a customer gets about an online order: placed, shipped, delivered, cancelled, and the steps of a
 * return. Each has a plain-text body and the same content as simple HTML; every value from the order is escaped.
 */
final class OrderEmailTemplates {

    private static final String SHOP = "Vaabhi";

    private OrderEmailTemplates() {}

    static EmailMessage forOrder(WebOrderEvent.Type type, WebOrder order, String storefrontUrl) {
        var email = new Email(storefrontUrl, order);
        switch (type) {
            case PLACED -> {
                email.subject = "Your " + SHOP + " order " + order.number() + " is placed";
                email.paragraph(
                        order.paymentMethod() == WebOrder.PaymentMethod.COD
                                ? "Thank you for your order. Please keep " + rupees(order.totalRupees())
                                        + " ready to pay when it is delivered."
                                : "Thank you for your order. We have received your payment of "
                                        + rupees(order.totalRupees()) + ".");
                email.items(order.lines());
                email.paragraph("Delivering to: " + address(order));
                email.paragraph("We will email you again when it ships.");
            }
            case SHIPPED -> {
                email.subject = "Your " + SHOP + " order " + order.number() + " has shipped";
                var courier = order.courier() == null ? "our delivery partner" : order.courier();
                email.paragraph("Your order is on its way with " + courier
                        + (order.trackingNumber() == null ? "." : ", tracking number " + order.trackingNumber() + "."));
                if (order.trackingUrl() != null) {
                    email.link("Track your parcel", order.trackingUrl());
                }
                if (order.paymentMethod() == WebOrder.PaymentMethod.COD) {
                    email.paragraph("Please keep " + rupees(order.totalRupees()) + " ready to pay on delivery.");
                }
            }
            case DELIVERED -> {
                email.subject = "Your " + SHOP + " order " + order.number() + " is delivered";
                email.paragraph("Your order has been delivered. We hope you love it.");
                if (order.returnableUntil() != null) {
                    email.paragraph("If something is not right, you can ask to return it from Your orders until "
                            + order.returnableUntil().toLocalDate() + ".");
                }
            }
            case CANCELLED -> {
                email.subject = "Your " + SHOP + " order " + order.number() + " is cancelled";
                email.paragraph("Your order has been cancelled"
                        + (order.cancelReason() == null ? "." : " (" + order.cancelReason() + ")."));
                if (order.refundedRupees() != null && order.refundedRupees().signum() > 0) {
                    email.paragraph(rupees(order.refundedRupees())
                            + " is being refunded to your original payment method. It usually reaches your account"
                            + " in 5–7 working days.");
                }
            }
        }
        return email.build();
    }

    /** Null when the return is not on the order (nothing to say). */
    static @Nullable EmailMessage forReturn(
            WebOrderReturnEvent.Type type, WebOrder order, String returnNumber, String storefrontUrl) {
        var request = order.returns().stream()
                .filter(r -> r.number().equals(returnNumber))
                .findFirst()
                .orElse(null);
        if (request == null) {
            return null;
        }
        var email = new Email(storefrontUrl, order);
        var what = request.lines().stream()
                .map(l -> l.quantity() + " × " + l.name())
                .reduce((a, b) -> a + ", " + b)
                .orElse("your items");
        switch (type) {
            case APPROVED -> {
                email.subject = "Your return " + returnNumber + " is approved";
                email.paragraph("We have approved your return of " + what + ".");
                email.note(request.shopNote());
                email.paragraph("We will let you know when the pickup is booked.");
            }
            case PICKUP_BOOKED -> {
                email.subject = "Pickup booked for your return " + returnNumber;
                email.paragraph("A courier"
                        + (request.pickupCourier() == null ? "" : " (" + request.pickupCourier() + ")")
                        + " will collect " + what + " from you"
                        + (request.pickupAwb() == null ? "." : ", pickup number " + request.pickupAwb() + ".")
                        + " Please keep the items packed and ready.");
            }
            case REJECTED -> {
                email.subject = "About your return " + returnNumber;
                email.paragraph("We are sorry, we could not accept the return of " + what + ".");
                email.note(request.shopNote());
            }
            case REFUNDED -> {
                email.subject = "Your refund for return " + returnNumber;
                var amount = request.refundRupees() == null ? BigDecimal.ZERO : request.refundRupees();
                email.paragraph("We have received " + what + ".");
                if (amount.signum() > 0) {
                    email.paragraph(
                            request.refundMethod() == WebOrder.RefundMethod.MANUAL
                                    ? rupees(amount) + " has been sent to you"
                                            + (request.refundReference() == null
                                                    ? "."
                                                    : " (reference " + request.refundReference() + ").")
                                    : rupees(amount) + " is being refunded to your original payment method. It usually"
                                            + " reaches your account in 5–7 working days.");
                }
            }
        }
        return email.build();
    }

    /** ₹1,25,000 or ₹99.50: Indian digit grouping (lakhs), paise only when there are any. */
    static String rupees(BigDecimal amount) {
        var rounded = amount.setScale(2, RoundingMode.HALF_UP);
        var digits = rounded.abs().toBigInteger().toString();
        var grouped = new StringBuilder();
        var head = digits.length() > 3 ? digits.substring(0, digits.length() - 3) : "";
        for (var i = 0; i < head.length(); i++) {
            grouped.append(head.charAt(i));
            if ((head.length() - i - 1) % 2 == 0 && i < head.length() - 1) {
                grouped.append(',');
            }
        }
        if (!head.isEmpty()) {
            grouped.append(',');
        }
        grouped.append(digits.length() > 3 ? digits.substring(digits.length() - 3) : digits);
        var paise = rounded.remainder(BigDecimal.ONE).abs().movePointRight(2).intValue();
        return (rounded.signum() < 0 ? "-₹" : "₹") + grouped + (paise == 0 ? "" : String.format(".%02d", paise));
    }

    private static String address(WebOrder order) {
        var parts = new ArrayList<String>();
        parts.add(order.contactName());
        parts.add(order.addressLine1());
        if (order.addressLine2() != null) {
            parts.add(order.addressLine2());
        }
        parts.add(order.city() + ", " + order.state() + " " + order.pincode());
        return String.join(", ", parts);
    }

    /** Collects the parts of one email, then writes the text and HTML bodies from them. */
    private static final class Email {
        private final String storefrontUrl;
        private final WebOrder order;
        private final List<String> paragraphs = new ArrayList<>();
        private final List<String[]> links = new ArrayList<>();
        private String subject = "";
        private List<WebOrder.Line> items = List.of();
        /** The items table follows this many paragraphs. */
        private int itemsAfter;

        Email(String storefrontUrl, WebOrder order) {
            this.storefrontUrl = storefrontUrl;
            this.order = order;
        }

        void paragraph(String text) {
            paragraphs.add(text);
        }

        void items(List<WebOrder.Line> lines) {
            items = lines;
            itemsAfter = paragraphs.size();
        }

        void note(@Nullable String shopNote) {
            if (shopNote != null && !shopNote.isBlank()) {
                paragraphs.add("Note from " + SHOP + ": " + shopNote);
            }
        }

        void link(String label, String url) {
            links.add(new String[] {label, url});
        }

        EmailMessage build() {
            var greeting = "Hi " + firstName(order.contactName()) + ",";
            var orders = storefrontUrl + "/account/orders";
            var text = new StringBuilder(greeting).append("\n\n");
            for (var i = 0; i <= paragraphs.size(); i++) {
                if (i == itemsAfter) {
                    textItems(text);
                }
                if (i < paragraphs.size()) {
                    text.append(paragraphs.get(i)).append("\n\n");
                }
            }
            links.forEach(l -> text.append(l[0]).append(": ").append(l[1]).append("\n\n"));
            text.append("Your orders: ").append(orders).append("\n\n");
            text.append("Order ")
                    .append(order.number())
                    .append(" · ")
                    .append(SHOP)
                    .append('\n');

            var html = new StringBuilder("<!doctype html><html><body style=\"margin:0;padding:24px;"
                    + "background:#f7f3ee;font-family:Arial,Helvetica,sans-serif;color:#2b2420\">"
                    + "<div style=\"max-width:560px;margin:0 auto;background:#fff;border-radius:8px;padding:24px\">"
                    + "<p style=\"font-size:20px;letter-spacing:4px;color:#6e2a35;margin:0 0 16px\">VAABHI</p>");
            html.append(p(escape(greeting)));
            for (var i = 0; i <= paragraphs.size(); i++) {
                if (i == itemsAfter) {
                    htmlItems(html);
                }
                if (i < paragraphs.size()) {
                    html.append(p(escape(paragraphs.get(i))));
                }
            }
            links.forEach(l -> html.append(button(l[0], l[1])));
            html.append(button("Your orders", orders));
            html.append("<p style=\"color:#8a7f76;font-size:12px;margin-top:24px\">Order ")
                    .append(escape(order.number()))
                    .append(" · ")
                    .append(SHOP)
                    .append("</p></div></body></html>");
            return new EmailMessage(
                    java.util.Objects.requireNonNull(order.contactEmail()), subject, text.toString(), html.toString());
        }

        private void textItems(StringBuilder text) {
            if (items.isEmpty()) {
                return;
            }
            items.forEach(line -> text.append("- ")
                    .append(line.quantity())
                    .append(" × ")
                    .append(itemName(line))
                    .append(": ")
                    .append(rupees(line.amountRupees()))
                    .append('\n'));
            text.append("Shipping: ")
                    .append(order.shippingRupees().signum() == 0 ? "Free" : rupees(order.shippingRupees()))
                    .append("\nTotal: ")
                    .append(rupees(order.totalRupees()))
                    .append("\n\n");
        }

        private void htmlItems(StringBuilder html) {
            if (items.isEmpty()) {
                return;
            }
            html.append("<table style=\"width:100%;border-collapse:collapse;margin:8px 0 16px\">");
            items.forEach(line -> html.append("<tr><td style=\"padding:6px 0;border-bottom:1px solid #eee\">")
                    .append(line.quantity())
                    .append(" × ")
                    .append(escape(itemName(line)))
                    .append("</td><td style=\"padding:6px 0;border-bottom:1px solid #eee;text-align:right\">")
                    .append(escape(rupees(line.amountRupees())))
                    .append("</td></tr>"));
            html.append(row(
                    "Shipping", order.shippingRupees().signum() == 0 ? "Free" : rupees(order.shippingRupees()), false));
            html.append(row("Total", rupees(order.totalRupees()), true));
            html.append("</table>");
        }

        private static String itemName(WebOrder.Line line) {
            var details = new ArrayList<String>();
            if (line.colourName() != null) {
                details.add(line.colourName());
            }
            if (line.size() != null) {
                details.add(line.size());
            }
            if (line.addonName() != null) {
                details.add(line.addonName());
            }
            return details.isEmpty() ? line.name() : line.name() + " (" + String.join(", ", details) + ")";
        }

        private static String firstName(String name) {
            var trimmed = name.trim();
            var space = trimmed.indexOf(' ');
            return space < 0 ? trimmed : trimmed.substring(0, space);
        }

        private static String p(String html) {
            return "<p style=\"font-size:15px;line-height:1.5;margin:0 0 12px\">" + html + "</p>";
        }

        private static String row(String label, String value, boolean strong) {
            var weight = strong ? "font-weight:bold;" : "";
            return "<tr><td style=\"padding:6px 0;" + weight + "\">" + label + "</td><td style=\"padding:6px 0;"
                    + weight + "text-align:right\">" + escape(value) + "</td></tr>";
        }

        private static String button(String label, String url) {
            return "<p style=\"margin:16px 0\"><a href=\"" + escape(url) + "\" style=\"background:#6e2a35;color:#fff;"
                    + "text-decoration:none;padding:10px 18px;border-radius:4px;display:inline-block\">"
                    + escape(label) + "</a></p>";
        }

        private static String escape(String value) {
            return HtmlUtils.htmlEscape(value);
        }
    }
}

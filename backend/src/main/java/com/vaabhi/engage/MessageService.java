package com.vaabhi.engage;

import com.vaabhi.settings.MessageTemplates;
import com.vaabhi.settings.SettingsService;
import com.vaabhi.tailoring.Client;
import com.vaabhi.tailoring.ClientService;
import com.vaabhi.tailoring.Order;
import com.vaabhi.tailoring.OrderPayment;
import com.vaabhi.tailoring.OrderService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer messages from the shop's templates: order updates, payment receipts, review requests and reward codes.
 * Every message is logged. With a {@link MessageProvider} set up it is sent; without one the message becomes a
 * wa.me / sms: link for staff to send from the shop's phone.
 */
@Service
public class MessageService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final OrderService orders;
    private final ClientService clients;
    private final SettingsService settings;
    private final ObjectProvider<MessageProvider> provider;

    MessageService(
            JdbcClient jdbc,
            OrderService orders,
            ClientService clients,
            SettingsService settings,
            ObjectProvider<MessageProvider> provider) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.clients = clients;
        this.settings = settings;
        this.provider = provider;
    }

    static String rupees(long paise) {
        var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMaximumFractionDigits(2);
        return "₹" + format.format(paise / 100.0);
    }

    /** wa.me wants digits only (with country code); sms: takes the number as stored. */
    static String link(MessageTemplates.Channel channel, String phone, String body) {
        var text = URLEncoder.encode(body, StandardCharsets.UTF_8).replace("+", "%20");
        return channel == MessageTemplates.Channel.WHATSAPP
                ? "https://wa.me/" + phone.replaceAll("[^0-9]", "") + "?text=" + text
                : "sms:" + phone + "?body=" + text;
    }

    @Transactional
    public Message prepare(Message.MessageForm form, @Nullable String actor) {
        var templates = settings.messages();
        var profile = settings.shopProfile();
        var channel = form.channel() != null ? form.channel() : templates.channel();
        Order order = form.orderId() == null ? null : orders.get(form.orderId());
        Long clientId = order != null ? Long.valueOf(order.client().id()) : form.clientId();
        if (clientId == null) {
            throw CouponService.bad("Choose the customer or the order");
        }
        Client client = clients.get(clientId);

        Map<String, String> values = new HashMap<>();
        values.put("customer", client.firstName());
        values.put("shop", profile.shopName());
        if (order != null) {
            values.put("order", order.number());
            values.put("amount", rupees(order.totalPaise()));
            values.put("balance", rupees(Math.max(0, order.balancePaise())));
            values.put("date", order.deliveryDate().format(DAY));
        }
        if (profile.googleReviewLink() != null) {
            values.put("link", profile.googleReviewLink());
        }
        if (form.code() != null && !form.code().isBlank()) {
            values.put("code", form.code().trim().toUpperCase());
        }

        String template = switch (form.purpose()) {
            case ORDER_PLACED -> needOrder(order, templates.orderPlaced());
            case ORDER_READY -> needOrder(order, templates.orderReady());
            case ORDER_DELIVERED -> needOrder(order, templates.orderDelivered());
            case PAYMENT -> {
                if (order == null) {
                    throw CouponService.bad("Choose the order");
                }
                var last = order.payments().stream()
                        .filter(p -> p.amountPaise() > 0)
                        .max(Comparator.comparing(OrderPayment::receivedAt));
                values.put("amount", rupees(last.map(OrderPayment::amountPaise).orElse(order.paidPaise())));
                yield templates.paymentReceived();
            }
            case REVIEW -> {
                if (profile.googleReviewLink() == null
                        || profile.googleReviewLink().isBlank()) {
                    throw CouponService.bad("Add the Google review link in Settings → Shop profile first");
                }
                yield templates.reviewRequest();
            }
            case REWARD -> {
                if (!values.containsKey("code")) {
                    throw CouponService.bad("Give the coupon code to send");
                }
                yield templates.reward();
            }
            case CUSTOM -> {
                if (form.body() == null || form.body().isBlank()) {
                    throw CouponService.bad("Write the message");
                }
                yield form.body().trim();
            }
        };
        var body = MessageTemplates.fill(template, values);
        var phone = channel == MessageTemplates.Channel.WHATSAPP && client.whatsapp() != null
                ? client.whatsapp()
                : client.phone();

        var status = Message.Status.LINK;
        String providerRef = null;
        String error = null;
        var sender = provider.getIfAvailable();
        if (sender != null && sender.configured()) {
            try {
                providerRef = sender.send(channel, phone, body);
                status = Message.Status.SENT;
            } catch (RuntimeException e) {
                status = Message.Status.FAILED;
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        long id = jdbc.sql("""
                        INSERT INTO message_log (channel, purpose, client_id, order_id, to_phone, body, status,
                            provider_ref, error, created_by)
                        VALUES (:channel, :purpose, :client, :order, :phone, :body, :status, :ref, :error, :by)
                        RETURNING id
                        """)
                .param("channel", channel.name())
                .param("purpose", form.purpose().name())
                .param("client", clientId)
                .param("order", order == null ? null : order.id())
                .param("phone", phone)
                .param("body", body.length() > 2000 ? body.substring(0, 2000) : body)
                .param("status", status.name())
                .param("ref", providerRef)
                .param("error", error == null || error.length() <= 300 ? error : error.substring(0, 300))
                .param("by", actor)
                .query(Long.class)
                .single();
        return list(null, null, id).getFirst();
    }

    private static String needOrder(@Nullable Order order, String template) {
        if (order == null) {
            throw CouponService.bad("Choose the order");
        }
        return template;
    }

    private record Row(
            long id,
            MessageTemplates.Channel channel,
            Message.Purpose purpose,
            @Nullable Long clientId,
            @Nullable String firstName,
            @Nullable String lastName,
            @Nullable Long orderId,
            @Nullable String orderNumber,
            String toPhone,
            String body,
            Message.Status status,
            @Nullable String error,
            @Nullable String createdBy,
            OffsetDateTime createdAt) {}

    /** Newest first, optionally for one customer or order (and one message by id). */
    @Transactional(readOnly = true)
    public List<Message> list(@Nullable Long clientId, @Nullable Long orderId, @Nullable Long id) {
        return jdbc
                .sql("""
                        SELECT m.id, m.channel, m.purpose, m.client_id, c.first_name, c.last_name, m.order_id,
                               o.number AS order_number, m.to_phone, m.body, m.status, m.error, m.created_by,
                               m.created_at
                        FROM message_log m LEFT JOIN client c ON c.id = m.client_id
                        LEFT JOIN shop_order o ON o.id = m.order_id
                        WHERE (CAST(:client AS bigint) IS NULL OR m.client_id = :client)
                          AND (CAST(:order AS bigint) IS NULL OR m.order_id = :order)
                          AND (CAST(:id AS bigint) IS NULL OR m.id = :id)
                        ORDER BY m.created_at DESC, m.id DESC LIMIT 200
                        """)
                .param("client", clientId)
                .param("order", orderId)
                .param("id", id)
                .query(Row.class)
                .list()
                .stream()
                .map(r -> new Message(
                        r.id(),
                        r.channel(),
                        r.purpose(),
                        r.clientId(),
                        r.firstName() == null
                                ? null
                                : r.lastName() == null ? r.firstName() : r.firstName() + " " + r.lastName(),
                        r.orderId(),
                        r.orderNumber(),
                        r.toPhone(),
                        r.body(),
                        r.status(),
                        r.status() == Message.Status.LINK ? link(r.channel(), r.toPhone(), r.body()) : null,
                        r.error(),
                        r.createdBy(),
                        r.createdAt()))
                .toList();
    }
}

package com.vaabhi.activity;

import com.vaabhi.inventory.InventoryService;
import com.vaabhi.settings.SettingsChanged;
import com.vaabhi.staff.RequestService;
import com.vaabhi.staff.StaffRequest;
import com.vaabhi.tailoring.AlterationEvent;
import com.vaabhi.tailoring.OrderEvent;
import com.vaabhi.tailoring.OrderService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the activity log from other modules' events (in the same transaction, so a rolled-back change leaves no
 * entry) and builds each person's notifications.
 */
@Service
public class ActivityService {

    /** Kinds shown in the notifications feed; the rest (e.g. settings changes) are only in the activity log. */
    static final List<String> FEED = List.of(
            "ORDER_PLACED",
            "ORDER_READY",
            "ORDER_DELIVERED",
            "ORDER_CANCELLED",
            "PAYMENT_RECEIVED",
            "ALTERATION_BOOKED",
            "ALTERATION_READY",
            "ALTERATION_DELIVERED");

    private final JdbcClient jdbc;
    private final OrderService orders;
    private final InventoryService inventory;
    private final RequestService requests;

    ActivityService(JdbcClient jdbc, OrderService orders, InventoryService inventory, RequestService requests) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.inventory = inventory;
        this.requests = requests;
    }

    private static String rupees(BigDecimal rupees) {
        var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMaximumFractionDigits(2);
        return "₹" + format.format(rupees);
    }

    void record(String kind, String title, @Nullable String actor, @Nullable String refType, @Nullable Long refId) {
        jdbc.sql("""
                        INSERT INTO activity_log (kind, title, actor, ref_type, ref_id)
                        VALUES (:kind, :title, :actor, :refType, :refId)
                        """)
                .param("kind", kind)
                .param("title", title.length() > 200 ? title.substring(0, 200) : title)
                .param("actor", actor)
                .param("refType", refType)
                .param("refId", refId)
                .update();
    }

    @EventListener
    void on(OrderEvent e) {
        var title = switch (e.type()) {
            case PLACED ->
                "Order " + e.number() + " booked for " + e.clientName() + " (" + rupees(e.amountRupees()) + ")";
            case CHANGED ->
                "Order " + e.number() + " for " + e.clientName() + " changed (" + rupees(e.amountRupees()) + ")";
            case READY -> "Order " + e.number() + " for " + e.clientName() + " is ready";
            case DELIVERED -> "Order " + e.number() + " delivered to " + e.clientName();
            case CANCELLED -> "Order " + e.number() + " for " + e.clientName() + " was cancelled";
            case PAYMENT_RECEIVED ->
                e.amountRupees().signum() < 0
                        ? "Refund of " + rupees(e.amountRupees().negate()) + " on " + e.number()
                        : rupees(e.amountRupees()) + " received for " + e.number() + " from " + e.clientName();
        };
        var kind = e.type() == OrderEvent.Type.PAYMENT_RECEIVED
                ? "PAYMENT_RECEIVED"
                : "ORDER_" + e.type().name();
        record(kind, title, e.actor(), "ORDER", e.orderId());
    }

    @EventListener
    void on(AlterationEvent e) {
        var title = switch (e.type()) {
            case BOOKED -> "Alteration " + e.number() + " booked for " + e.clientName();
            case READY -> "Alteration " + e.number() + " for " + e.clientName() + " is ready";
            case DELIVERED -> "Alteration " + e.number() + " handed over to " + e.clientName();
            case CANCELLED -> "Alteration " + e.number() + " was cancelled";
            case PAYMENT_RECEIVED -> rupees(e.amountRupees()) + " received for alteration " + e.number();
        };
        var kind = e.type() == AlterationEvent.Type.PAYMENT_RECEIVED ? "PAYMENT_RECEIVED" : "ALTERATION_" + e.type();
        record(kind, title, e.actor(), "ALTERATION", e.alterationId());
    }

    @EventListener
    void on(SettingsChanged e) {
        record("SETTINGS_CHANGED", e.title(), e.actor(), "SETTINGS", null);
    }

    private static final String SELECT = "SELECT id, at, kind, title, actor, ref_type, ref_id FROM activity_log";

    /** The activity log, newest first, optionally of one kind or about one thing. */
    // Only constant SQL fragments (and fixed table names / enum-chosen ORDER BY) are joined; values are bound.
    @SuppressWarnings("java:S2077")
    @Transactional(readOnly = true)
    public Activity.ActivityPage log(
            @Nullable String kind, @Nullable String refType, @Nullable Long refId, int page, int pageSize) {
        var where = """
                 WHERE (CAST(:kind AS varchar) IS NULL OR kind = :kind)
                   AND (CAST(:refType AS varchar) IS NULL OR ref_type = :refType)
                   AND (CAST(:refId AS bigint) IS NULL OR ref_id = :refId)
                """;
        long total = jdbc.sql("SELECT COUNT(*) FROM activity_log" + where)
                .param("kind", kind)
                .param("refType", refType)
                .param("refId", refId)
                .query(Long.class)
                .single();
        var items = jdbc.sql(SELECT + where + " ORDER BY at DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("kind", kind)
                .param("refType", refType)
                .param("refId", refId)
                .param("limit", pageSize)
                .param("offset", (page - 1) * pageSize)
                .query(Activity.ActivityEntry.class)
                .list();
        return new Activity.ActivityPage(items, total, page, pageSize);
    }

    /** Live alerts, the last 30 feed entries, and how many of them came since the person last looked. */
    @Transactional(readOnly = true)
    public Activity.Notifications notifications(long staffId) {
        var alerts = new ArrayList<Activity.Alert>();
        alert(
                alerts,
                "OVERDUE",
                "orders past their delivery date",
                count(OrderService.Filter.OVERDUE),
                "/orders/?filter=OVERDUE");
        alert(
                alerts,
                "DUE_TODAY",
                "orders due today",
                count(OrderService.Filter.DUE_TODAY),
                "/orders/?filter=DUE_TODAY");
        alert(alerts, "READY", "orders ready for pickup", count(OrderService.Filter.READY), "/orders/?filter=READY");
        alert(alerts, "LOW_STOCK", "stock items running low", inventory.lowStockCount(), "/stock/?low=1");
        alert(
                alerts,
                "REQUESTS",
                "staff requests waiting",
                requests.list(StaffRequest.Status.PENDING, null).size(),
                "/staff/requests/");
        var recent = jdbc.sql(SELECT + " WHERE kind = ANY(:kinds) ORDER BY at DESC, id DESC LIMIT 30")
                .param("kinds", FEED.toArray(String[]::new))
                .query(Activity.ActivityEntry.class)
                .list();
        var seen = jdbc.sql("SELECT seen_at FROM notification_seen WHERE staff_id = :id")
                .param("id", staffId)
                .query(java.time.OffsetDateTime.class)
                .optional();
        int unread = (int) recent.stream()
                .filter(e -> seen.isEmpty() || e.at().isAfter(seen.get()))
                .count();
        return new Activity.Notifications(alerts, recent, unread);
    }

    private int count(OrderService.Filter filter) {
        return (int) orders.list(null, filter, null, null, null, 1, 1).total();
    }

    private static void alert(List<Activity.Alert> alerts, String kind, String what, int count, String href) {
        if (count > 0) {
            alerts.add(new Activity.Alert(kind, count + " " + what, count, href));
        }
    }

    /** Marks everything up to now as seen. */
    @Transactional
    public void markSeen(long staffId) {
        jdbc.sql("""
                        INSERT INTO notification_seen (staff_id, seen_at) VALUES (:id, now())
                        ON CONFLICT (staff_id) DO UPDATE SET seen_at = EXCLUDED.seen_at
                        """).param("id", staffId).update();
    }
}

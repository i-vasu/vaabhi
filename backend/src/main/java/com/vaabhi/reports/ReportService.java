package com.vaabhi.reports;

import com.vaabhi.money.LedgerService;
import com.vaabhi.payroll.PayrollService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The reports. Each one is a single query over a date range (inclusive, in the shop's time zone) returning rows
 * keyed by column; money is in rupees, dates are yyyy-mm-dd text.
 */
@Service
public class ReportService {

    /** Longest range a report may cover. */
    static final long MAX_DAYS = 400;

    static final List<Report.ReportInfo> CATALOGUE = List.of(
            new Report.ReportInfo("sales", "Sales", "Invoiced per day: taxable value, GST and total"),
            new Report.ReportInfo("gst", "GST register", "Every invoice with GSTIN, taxable value, CGST and SGST"),
            new Report.ReportInfo("sales-orders", "Sales orders", "Orders booked with what is paid and owed"),
            new Report.ReportInfo("orders", "Orders by status", "How many orders are where, and their value"),
            new Report.ReportInfo("deliveries", "Deliveries", "Orders handed over, on time or late"),
            new Report.ReportInfo("customers", "Customers", "Top customers by spend, with what they owe"),
            new Report.ReportInfo("alterations", "Alterations", "Alterations by kind of work and charges"),
            new Report.ReportInfo("workshop", "Workshop", "Items finished per stage and time spent in each"),
            new Report.ReportInfo("production", "Production", "Pieces each person finished, at piece rates"),
            new Report.ReportInfo("work", "Attendance", "Days present, absent and on leave; late and overtime"),
            new Report.ReportInfo("stock", "Stock", "On hand, in and out, and stock value"),
            new Report.ReportInfo("rewards", "Rewards", "Points earned and redeemed per customer"),
            new Report.ReportInfo(
                    "profitability", "Product profitability", "Price-list items: revenue, piece cost, profit"),
            new Report.ReportInfo("ledger", "Financial ledger", "Every payment, refund and expense"),
            new Report.ReportInfo("expenses", "Expenses", "Spending by category"),
            new Report.ReportInfo("usage", "Usage", "Orders, messages, photos and other activity"));

    private static final String CUSTOMER = "c.first_name || COALESCE(' ' || c.last_name, '')";

    private final JdbcClient jdbc;
    private final LedgerService ledger;
    private final PayrollService payroll;
    private final Clock clock;

    ReportService(JdbcClient jdbc, LedgerService ledger, PayrollService payroll, Clock clock) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.payroll = payroll;
        this.clock = clock;
    }

    private static Report.Column col(String key, String label, Report.Type type) {
        return new Report.Column(key, label, type);
    }

    private static Report.Column text(String key, String label) {
        return col(key, label, Report.Type.TEXT);
    }

    private static Report.Column number(String key, String label) {
        return col(key, label, Report.Type.NUMBER);
    }

    private static Report.Column money(String key, String label) {
        return col(key, label, Report.Type.MONEY);
    }

    /** Range parameters: dates, and the start/end instants of those days in the shop's zone. */
    private Map<String, Object> range(LocalDate from, LocalDate to) {
        var zone = clock.getZone();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", from);
        params.put("to", to);
        params.put("start", from.atStartOfDay(zone).toOffsetDateTime());
        params.put("end", to.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
        params.put("zone", zone.getId());
        return params;
    }

    private List<Map<String, Object>> rows(String sql, Map<String, Object> params) {
        return jdbc.sql(sql).params(params).query().listOfRows();
    }

    /** A money column from a generic row: rupees with two decimals (zero when null). */
    private static BigDecimal money(@Nullable Object value) {
        return value == null ? Rupees.ZERO : Rupees.of(new BigDecimal(value.toString()));
    }

    /** Sums the given numeric columns over the rows. */
    static Map<String, Object> totals(List<Map<String, Object>> rows, String... keys) {
        Map<String, Object> totals = new LinkedHashMap<>();
        for (var key : keys) {
            var sum = BigDecimal.ZERO;
            for (var row : rows) {
                if (row.get(key) instanceof Number n) {
                    sum = sum.add(new BigDecimal(n.toString()));
                }
            }
            totals.put(key, sum.stripTrailingZeros().scale() <= 0 ? sum.longValue() : sum);
        }
        return totals;
    }

    @Transactional(readOnly = true)
    public Report report(String name, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end date is before the start date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a range of at most " + MAX_DAYS + " days");
        }
        var info = CATALOGUE.stream()
                .filter(r -> r.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No report called " + name));
        var p = range(from, to);
        return switch (name) {
            case "sales" -> {
                var rows = rows("""
                        SELECT to_char(v.issued_on, 'YYYY-MM-DD') AS day, COUNT(*) AS invoices,
                               SUM(v.taxable_rupees) AS taxable, SUM(v.cgst_rupees + v.sgst_rupees) AS gst,
                               SUM(v.total_rupees) AS total
                        FROM invoice v WHERE v.cancelled_at IS NULL AND v.issued_on BETWEEN :from AND :to
                        GROUP BY v.issued_on ORDER BY v.issued_on
                        """, p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                col("day", "Day", Report.Type.DATE),
                                number("invoices", "Invoices"),
                                money("taxable", "Taxable"),
                                money("gst", "GST"),
                                money("total", "Total")),
                        rows,
                        totals(rows, "invoices", "taxable", "gst", "total"),
                        "Cancelled invoices are left out.");
            }
            case "gst" -> {
                var rows = rows("""
                        SELECT v.number, to_char(v.issued_on, 'YYYY-MM-DD') AS date, %s AS customer,
                               v.client_gstin AS gstin, v.gst_rate AS rate, v.taxable_rupees AS taxable,
                               v.cgst_rupees AS cgst, v.sgst_rupees AS sgst, v.total_rupees AS total
                        FROM invoice v JOIN client c ON c.id = v.client_id
                        WHERE v.cancelled_at IS NULL AND v.issued_on BETWEEN :from AND :to
                        ORDER BY v.issued_on, v.number
                        """.formatted(CUSTOMER), p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("number", "Invoice"),
                                col("date", "Date", Report.Type.DATE),
                                text("customer", "Customer"),
                                text("gstin", "Customer GSTIN"),
                                col("rate", "GST %", Report.Type.PERCENT),
                                money("taxable", "Taxable"),
                                money("cgst", "CGST"),
                                money("sgst", "SGST"),
                                money("total", "Total")),
                        rows,
                        totals(rows, "taxable", "cgst", "sgst", "total"),
                        null);
            }
            case "sales-orders" -> {
                var rows = rows("""
                        SELECT o.number, to_char(o.order_date, 'YYYY-MM-DD') AS date, %s AS customer,
                               (SELECT string_agg(i.description, ', ' ORDER BY i.position) FROM order_item i
                                WHERE i.order_id = o.id) AS items,
                               o.status, o.total_rupees AS total, o.paid_rupees AS paid,
                               o.total_rupees - o.paid_rupees AS balance
                        FROM shop_order o JOIN client c ON c.id = o.client_id
                        WHERE o.order_date BETWEEN :from AND :to AND o.status <> 'CANCELLED'
                        ORDER BY o.order_date, o.id
                        """.formatted(CUSTOMER), p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("number", "Order"),
                                col("date", "Booked", Report.Type.DATE),
                                text("customer", "Customer"),
                                text("items", "Items"),
                                text("status", "Status"),
                                money("total", "Total"),
                                money("paid", "Paid"),
                                money("balance", "Balance")),
                        rows,
                        totals(rows, "total", "paid", "balance"),
                        "Cancelled orders are left out.");
            }
            case "orders" -> {
                var rows = rows("""
                        SELECT o.status, COUNT(*) AS orders, SUM(o.total_rupees) AS value
                        FROM shop_order o WHERE o.order_date BETWEEN :from AND :to
                        GROUP BY o.status ORDER BY COUNT(*) DESC
                        """, p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(text("status", "Status"), number("orders", "Orders"), money("value", "Value")),
                        rows,
                        totals(rows, "orders", "value"),
                        "Orders booked in the range, by where they are now.");
            }
            case "deliveries" -> {
                var rows = rows("""
                        SELECT o.number, %s AS customer, to_char(o.delivery_date, 'YYYY-MM-DD') AS promised,
                               to_char((o.delivered_at AT TIME ZONE :zone)::date, 'YYYY-MM-DD') AS delivered,
                               GREATEST(0, (o.delivered_at AT TIME ZONE :zone)::date - o.delivery_date) AS days_late,
                               o.total_rupees AS total
                        FROM shop_order o JOIN client c ON c.id = o.client_id
                        WHERE o.status = 'DELIVERED' AND o.delivered_at >= :start AND o.delivered_at < :end
                        ORDER BY o.delivered_at
                        """.formatted(CUSTOMER), p);
                long late = rows.stream()
                        .filter(r -> ((Number) r.get("days_late")).intValue() > 0)
                        .count();
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("number", "Order"),
                                text("customer", "Customer"),
                                col("promised", "Promised", Report.Type.DATE),
                                col("delivered", "Delivered", Report.Type.DATE),
                                number("days_late", "Days late"),
                                money("total", "Total")),
                        rows,
                        totals(rows, "total"),
                        rows.isEmpty() ? null : (rows.size() - late) + " of " + rows.size() + " on time.");
            }
            case "customers" -> {
                var rows = rows("""
                        SELECT %s AS customer, c.phone, COUNT(o.id) AS orders, SUM(o.total_rupees) AS spent,
                               SUM(GREATEST(o.total_rupees - o.paid_rupees, 0)) AS owing
                        FROM shop_order o JOIN client c ON c.id = o.client_id
                        WHERE o.order_date BETWEEN :from AND :to AND o.status <> 'CANCELLED'
                        GROUP BY c.id ORDER BY spent DESC LIMIT 500
                        """.formatted(CUSTOMER), p);
                long added = jdbc.sql("SELECT COUNT(*) FROM client WHERE created_at >= :start AND created_at < :end")
                        .params(p)
                        .query(Long.class)
                        .single();
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("customer", "Customer"),
                                text("phone", "Mobile"),
                                number("orders", "Orders"),
                                money("spent", "Spent"),
                                money("owing", "Owing")),
                        rows,
                        totals(rows, "orders", "spent", "owing"),
                        added + " new customers in this range.");
            }
            case "alterations" -> {
                var rows = rows("""
                        SELECT i.kind, COUNT(DISTINCT a.id) AS alterations, COUNT(*) AS garments,
                               SUM(i.cost_rupees) AS charged
                        FROM alteration a JOIN alteration_item i ON i.alteration_id = a.id
                        WHERE a.created_at >= :start AND a.created_at < :end AND a.status <> 'CANCELLED'
                        GROUP BY i.kind ORDER BY COUNT(*) DESC
                        """, p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("kind", "Work"), number("alterations", "Alterations"),
                                number("garments", "Garments"), money("charged", "Charged")),
                        rows,
                        totals(rows, "garments", "charged"),
                        null);
            }
            case "workshop" -> {
                var rows = rows("""
                        WITH moves AS (
                            SELECT l.*, LAG(l.moved_at) OVER (PARTITION BY l.item_id ORDER BY l.moved_at, l.id) AS prev
                            FROM order_item_stage_log l)
                        SELECT st.name AS stage, SUM(i.quantity) AS finished,
                               ROUND(AVG(EXTRACT(EPOCH FROM (m.moved_at - COALESCE(m.prev, o.created_at))) / 3600)::numeric,
                                     1) AS avg_hours
                        FROM moves m JOIN production_stage st ON st.id = m.from_stage_id
                        JOIN order_item i ON i.id = m.item_id JOIN shop_order o ON o.id = i.order_id
                        WHERE st.kind = 'WORK' AND m.moved_at >= :start AND m.moved_at < :end
                        GROUP BY st.id, st.name, st.position ORDER BY st.position
                        """, p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("stage", "Stage"),
                                number("finished", "Pieces finished"),
                                number("avg_hours", "Average hours in stage")),
                        rows,
                        totals(rows, "finished"),
                        "Time in a stage runs from arriving in it to leaving it.");
            }
            case "production" -> {
                Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
                for (var e : payroll.production(from, to, null).entries()) {
                    var row = grouped.computeIfAbsent(e.staffName() + "\u0000" + e.stageName(), k -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("staff", e.staffName());
                        r.put("stage", e.stageName());
                        r.put("pieces", 0L);
                        r.put("amount", Rupees.ZERO);
                        return r;
                    });
                    row.put("pieces", (Long) row.get("pieces") + e.quantity());
                    row.put("amount", ((BigDecimal) row.get("amount")).add(e.amountRupees()));
                }
                var rows = new ArrayList<>(grouped.values());
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("staff", "Person"),
                                text("stage", "Stage"),
                                number("pieces", "Pieces"),
                                money("amount", "At piece rates")),
                        rows,
                        totals(rows, "pieces", "amount"),
                        null);
            }
            case "work" -> {
                var rows = rows("""
                        SELECT s.name AS staff,
                               COUNT(*) FILTER (WHERE a.status = 'PRESENT') AS present,
                               COUNT(*) FILTER (WHERE a.status = 'HALF_DAY') AS half_days,
                               COUNT(*) FILTER (WHERE a.status = 'ABSENT') AS absent,
                               COUNT(*) FILTER (WHERE a.status = 'LEAVE') AS leave,
                               SUM(a.late_minutes) AS late_minutes, SUM(a.overtime_minutes) AS overtime_minutes
                        FROM attendance a JOIN staff_account s ON s.id = a.staff_id
                        WHERE a.day BETWEEN :from AND :to
                        GROUP BY s.id, s.name ORDER BY lower(s.name)
                        """, p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("staff", "Person"),
                                number("present", "Present"),
                                number("half_days", "Half days"),
                                number("absent", "Absent"),
                                number("leave", "Leave"),
                                number("late_minutes", "Late (min)"),
                                number("overtime_minutes", "Overtime (min)")),
                        rows,
                        totals(rows, "present", "absent", "leave", "late_minutes", "overtime_minutes"),
                        null);
            }
            case "stock" -> {
                var rows = rows("""
                        SELECT i.name, i.unit, COALESCE(SUM(m.quantity), 0) AS on_hand,
                               COALESCE(SUM(m.quantity) FILTER (WHERE m.quantity > 0 AND m.moved_at >= :start
                                                                AND m.moved_at < :end), 0) AS moved_in,
                               COALESCE(-SUM(m.quantity) FILTER (WHERE m.quantity < 0 AND m.moved_at >= :start
                                                                 AND m.moved_at < :end), 0) AS moved_out,
                               i.reorder_level, ROUND(GREATEST(COALESCE(SUM(m.quantity), 0), 0) * i.cost_rupees) AS value
                        FROM stock_item i LEFT JOIN stock_movement m ON m.item_id = i.id
                        WHERE i.active GROUP BY i.id ORDER BY lower(i.name)
                        """, p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("name", "Item"),
                                text("unit", "Unit"),
                                number("on_hand", "On hand"),
                                number("moved_in", "In"),
                                number("moved_out", "Out"),
                                number("reorder_level", "Reorder at"),
                                money("value", "Value")),
                        rows,
                        totals(rows, "value"),
                        "On hand and value are as of now; in and out are for the range.");
            }
            case "rewards" -> {
                var rows = rows("""
                        SELECT %s AS customer,
                               COALESCE(SUM(r.points) FILTER (WHERE r.kind = 'EARNED'), 0) AS earned,
                               COALESCE(-SUM(r.points) FILTER (WHERE r.kind IN ('REDEEMED', 'RETURNED')), 0) AS redeemed,
                               (SELECT COALESCE(SUM(x.points), 0) FROM reward_entry x WHERE x.client_id = c.id) AS balance
                        FROM reward_entry r JOIN client c ON c.id = r.client_id
                        WHERE r.created_at >= :start AND r.created_at < :end
                        GROUP BY c.id ORDER BY earned DESC
                        """.formatted(CUSTOMER), p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("customer", "Customer"), number("earned", "Earned"),
                                number("redeemed", "Redeemed"), number("balance", "Balance now")),
                        rows,
                        totals(rows, "earned", "redeemed"),
                        null);
            }
            case "profitability" -> {
                var rows = rows("""
                        SELECT p.code, p.name, t.name AS garment, SUM(i.quantity) AS pieces,
                               SUM(i.price_rupees * i.quantity) AS revenue,
                               SUM(i.quantity) * (SELECT COALESCE(SUM((c ->> 'rupees')::numeric), 0)
                                                  FROM jsonb_array_elements(p.stage_costs) c) AS piece_cost
                        FROM order_item i JOIN service_product p ON p.id = i.service_product_id
                        JOIN garment_template t ON t.id = p.template_id JOIN shop_order o ON o.id = i.order_id
                        WHERE o.status <> 'CANCELLED' AND o.order_date BETWEEN :from AND :to
                        GROUP BY p.id, p.code, p.name, t.name ORDER BY revenue DESC
                        """, p);
                for (var row : rows) {
                    var revenue = money(row.get("revenue"));
                    var profit = revenue.subtract(money(row.get("piece_cost")));
                    row.put("profit", profit);
                    row.put(
                            "margin",
                            revenue.signum() == 0
                                    ? 0
                                    : profit.multiply(BigDecimal.valueOf(100))
                                            .divide(revenue, 1, RoundingMode.HALF_UP)
                                            .doubleValue());
                }
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                text("code", "Code"),
                                text("name", "Item"),
                                text("garment", "Garment"),
                                number("pieces", "Pieces"),
                                money("revenue", "Revenue"),
                                money("piece_cost", "Piece cost"),
                                money("profit", "Profit"),
                                col("margin", "Margin %", Report.Type.PERCENT)),
                        rows,
                        totals(rows, "pieces", "revenue", "piece_cost", "profit"),
                        "Revenue is at the price on the order before discounts; piece cost uses today's piece rates.");
            }
            case "ledger" -> {
                var l = ledger.ledger(from, to, null);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (var e : l.entries()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put(
                            "date",
                            e.at().atZoneSameInstant(clock.getZone())
                                    .toLocalDate()
                                    .toString());
                    r.put("kind", e.kind().name());
                    r.put("reference", e.reference());
                    r.put("party", e.party());
                    r.put("mode", e.mode().name());
                    r.put("amount", e.amountRupees());
                    r.put("balance", e.balanceRupees());
                    rows.add(r);
                }
                Map<String, Object> totals = new LinkedHashMap<>();
                totals.put("amount", l.inRupees().subtract(l.outRupees()));
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(
                                col("date", "Date", Report.Type.DATE),
                                text("kind", "Kind"),
                                text("reference", "Reference"),
                                text("party", "Party"),
                                text("mode", "Mode"),
                                money("amount", "Amount"),
                                money("balance", "Balance")),
                        rows,
                        totals,
                        "Opening balance ₹" + l.openingRupees().toPlainString() + "; closing ₹"
                                + l.closingRupees().toPlainString() + ".");
            }
            case "expenses" -> {
                var rows = rows("""
                        SELECT category, COUNT(*) AS entries, SUM(amount_rupees) AS amount
                        FROM expense WHERE spent_on BETWEEN :from AND :to
                        GROUP BY category ORDER BY SUM(amount_rupees) DESC
                        """, p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(text("category", "Category"), number("entries", "Entries"), money("amount", "Amount")),
                        rows,
                        totals(rows, "entries", "amount"),
                        null);
            }
            case "usage" -> {
                var rows = rows("""
                        SELECT 'Orders booked' AS what, COUNT(*) AS count FROM shop_order
                            WHERE created_at >= :start AND created_at < :end
                        UNION ALL SELECT 'Invoices issued', COUNT(*) FROM invoice
                            WHERE created_at >= :start AND created_at < :end
                        UNION ALL SELECT 'WhatsApp messages', COUNT(*) FROM message_log
                            WHERE channel = 'WHATSAPP' AND created_at >= :start AND created_at < :end
                        UNION ALL SELECT 'SMS messages', COUNT(*) FROM message_log
                            WHERE channel = 'SMS' AND created_at >= :start AND created_at < :end
                        UNION ALL SELECT 'Messages sent by a provider', COUNT(*) FROM message_log
                            WHERE status = 'SENT' AND created_at >= :start AND created_at < :end
                        UNION ALL SELECT 'Gallery photos added', COUNT(*) FROM gallery_photo
                            WHERE created_at >= :start AND created_at < :end
                        UNION ALL SELECT 'Active staff', COUNT(*) FROM staff_account WHERE active
                        UNION ALL SELECT 'Customers', COUNT(*) FROM client
                        """, p);
                yield new Report(
                        name,
                        info.title(),
                        from,
                        to,
                        List.of(text("what", "What"), number("count", "Count")),
                        rows,
                        Map.of(),
                        "The dashboard has no AI features yet, so there is no AI usage to report.");
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report called " + name);
        };
    }

    // --- overview ---------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Report.ReportsOverview overview() {
        var today = LocalDate.now(clock);
        var month = YearMonth.from(today);
        var p = range(month.atDay(1), today);
        var figures = jdbc.sql("""
                        SELECT
                          (SELECT COALESCE(SUM(total_rupees), 0) FROM shop_order
                             WHERE order_date BETWEEN :from AND :to AND status <> 'CANCELLED') AS booked,
                          (SELECT COUNT(*) FROM shop_order
                             WHERE order_date BETWEEN :from AND :to AND status <> 'CANCELLED') AS booked_count,
                          (SELECT COUNT(*) FROM shop_order
                             WHERE delivered_at >= :start AND delivered_at < :end) AS delivered_count,
                          (SELECT COALESCE(SUM(amount_rupees), 0) FROM order_payment
                             WHERE received_at >= :start AND received_at < :end)
                          + (SELECT COALESCE(SUM(amount_rupees), 0) FROM alteration_payment
                             WHERE received_at >= :start AND received_at < :end) AS collected,
                          (SELECT COALESCE(SUM(amount_rupees), 0) FROM expense
                             WHERE spent_on BETWEEN :from AND :to) AS expenses,
                          (SELECT COUNT(*) FROM client WHERE created_at >= :start AND created_at < :end) AS new_clients
                        """).params(p).query().singleRow();
        var booked = money(figures.get("booked"));
        int bookedCount = ((Number) figures.get("booked_count")).intValue();

        var months = new ArrayList<Report.MonthFigures>();
        for (int back = 5; back >= 0; back--) {
            var m = month.minusMonths(back);
            var mp = range(m.atDay(1), m.atEndOfMonth());
            var row = jdbc.sql("""
                            SELECT
                              (SELECT COALESCE(SUM(total_rupees), 0) FROM shop_order
                                 WHERE order_date BETWEEN :from AND :to AND status <> 'CANCELLED') AS booked,
                              (SELECT COALESCE(SUM(amount_rupees), 0) FROM order_payment
                                 WHERE received_at >= :start AND received_at < :end)
                              + (SELECT COALESCE(SUM(amount_rupees), 0) FROM alteration_payment
                                 WHERE received_at >= :start AND received_at < :end) AS collected,
                              (SELECT COALESCE(SUM(amount_rupees), 0) FROM expense
                                 WHERE spent_on BETWEEN :from AND :to) AS expenses
                            """).params(mp).query().singleRow();
            months.add(new Report.MonthFigures(
                    m.toString(), money(row.get("booked")), money(row.get("collected")), money(row.get("expenses"))));
        }

        var dues = ledger.outstanding(null);
        String[] labels = {"Not due yet", "1–7 days late", "8–30 days late", "31–60 days late", "Over 60 days late"};
        int[] counts = new int[labels.length];
        var rupees = new BigDecimal[labels.length];
        Arrays.fill(rupees, Rupees.ZERO);
        for (var due : dues.items()) {
            int d = due.daysLate();
            int bucket = d == 0 ? 0 : d <= 7 ? 1 : d <= 30 ? 2 : d <= 60 ? 3 : 4;
            counts[bucket]++;
            rupees[bucket] = rupees[bucket].add(due.dueRupees());
        }
        var ageing = new ArrayList<Report.AgeingBucket>();
        for (int i = 0; i < labels.length; i++) {
            ageing.add(new Report.AgeingBucket(labels[i], counts[i], rupees[i]));
        }

        return new Report.ReportsOverview(
                booked,
                money(figures.get("collected")),
                money(figures.get("expenses")),
                bookedCount,
                ((Number) figures.get("delivered_count")).intValue(),
                bookedCount == 0 ? Rupees.ZERO : Rupees.divide(booked, BigDecimal.valueOf(bookedCount)),
                ((Number) figures.get("new_clients")).intValue(),
                dues.totalRupees(),
                months,
                ageing);
    }
}

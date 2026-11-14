package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.BooksSettingsService;
import com.vaabhi.accounts.ChartService;
import com.vaabhi.accounts.NewEntryLine;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Shared lookups and line builders for the posting rules. */
@Component
class RuleSupport {

    static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    /** Appended to a pending query whose source table is aliased {@code s}. */
    static final String NOT_POSTED = """
            NOT EXISTS (SELECT 1 FROM journal_entry j
                        WHERE j.source_type = :type AND j.source_id = s.id AND j.source_step = :step)
            """;

    /** The IST date of a timestamp column. */
    static String istDate(String column) {
        return "CAST(" + column + " AT TIME ZONE 'Asia/Kolkata' AS date)";
    }

    private final JdbcClient jdbc;
    private final ChartService chart;
    private final BooksSettingsService settings;

    RuleSupport(JdbcClient jdbc, ChartService chart, BooksSettingsService settings) {
        this.jdbc = jdbc;
        this.chart = chart;
        this.settings = settings;
    }

    JdbcClient jdbc() {
        return jdbc;
    }

    long account(String systemKey) {
        return chart.idOf(systemKey);
    }

    long modeAccount(String mode) {
        return settings.accountFor(mode);
    }

    /** The supplier party Shiprocket's freight and COD deductions go to, or null when it is not set. */
    @Nullable
    Long shiprocketPartyId() {
        return settings.shiprocketPartyId();
    }

    static LocalDate day(OffsetDateTime at) {
        return at.atZoneSameInstant(SHOP_ZONE).toLocalDate();
    }

    static String shopKey(long orderId) {
        return "SHOP:" + orderId;
    }

    static String webKey(long orderId) {
        return "WEB:" + orderId;
    }

    static @Nullable String orderKey(@Nullable Long shopOrderId, @Nullable Long webOrderId) {
        if (shopOrderId != null) {
            return shopKey(shopOrderId);
        }
        return webOrderId == null ? null : webKey(webOrderId);
    }

    List<Long> pending(String sql, String type, String step, LocalDate start, int limit) {
        return jdbc.sql(sql)
                .param("type", type)
                .param("step", step)
                .param("start", start)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    /** True when the shop order had an invoice issued by {@code at} and not cancelled by then. */
    boolean shopInvoiceLive(long orderId, OffsetDateTime at) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM invoice
                                       WHERE order_id = :id AND created_at <= :at
                                         AND (cancelled_at IS NULL OR cancelled_at > :at))
                        """)
                .param("id", orderId)
                .param("at", at)
                .query(Boolean.class)
                .single();
    }

    boolean webInvoiceLive(long orderId, OffsetDateTime at) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM invoice
                                       WHERE web_order_id = :id AND created_at <= :at
                                         AND (cancelled_at IS NULL OR cancelled_at > :at))
                        """)
                .param("id", orderId)
                .param("at", at)
                .query(Boolean.class)
                .single();
    }

    /** The mode a web order was paid with: RAZORPAY for online orders, COD otherwise. */
    static String webMode(String paymentMethod) {
        return "COD".equals(paymentMethod) ? "COD" : "RAZORPAY";
    }

    static NewEntryLine debit(long account, @Nullable Long party, BigDecimal amount) {
        return new NewEntryLine(account, party, Rupees.of(amount), Rupees.ZERO, null);
    }

    static NewEntryLine credit(long account, @Nullable Long party, BigDecimal amount) {
        return new NewEntryLine(account, party, Rupees.ZERO, Rupees.of(amount), null);
    }

    /** Adds {@code line} only when the amount is above zero (a zero line is not a valid journal line). */
    static void addIfPositive(List<NewEntryLine> lines, BigDecimal amount, NewEntryLine line) {
        if (amount.signum() > 0) {
            lines.add(line);
        }
    }

    /** Credit lines for the GST parts of a sale, or debit lines for a return. Zero parts are left out. */
    List<NewEntryLine> gstLines(
            boolean debit, @Nullable Long party, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
        var lines = new ArrayList<NewEntryLine>();
        addIfPositive(lines, cgst, line(debit, account("OUTPUT_CGST"), party, cgst));
        addIfPositive(lines, sgst, line(debit, account("OUTPUT_SGST"), party, sgst));
        addIfPositive(lines, igst, line(debit, account("OUTPUT_IGST"), party, igst));
        return lines;
    }

    static NewEntryLine line(boolean debit, long account, @Nullable Long party, BigDecimal amount) {
        return debit ? debit(account, party, amount) : credit(account, party, amount);
    }
}

package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Shared setup for the posting rule tests: the books start date and SQL-seeded source records. */
abstract class BooksFixtures {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected BooksSettingsService settings;

    @Autowired
    protected JournalSweeper sweeper;

    @Autowired
    protected PostingProblemService problems;

    @Autowired
    protected DailyBooksCheck check;

    private @Nullable LocalDate originalStart;

    protected final OffsetDateTime base = OffsetDateTime.now();

    @BeforeEach
    void startBooks() {
        originalStart = settings.get().startDate();
        var start = LocalDate.now(IST).withDayOfMonth(1);
        settings.save(new BooksSettings(start, settings.get().modeAccounts(), null), null);
        jdbc.sql("UPDATE books_setting SET start_date = :start WHERE id = 1")
                .param("start", start)
                .update();
    }

    @AfterEach
    void restoreStart() {
        jdbc.sql("UPDATE books_setting SET start_date = :start WHERE id = 1")
                .param("start", originalStart)
                .update();
    }

    protected static int next() {
        return SEQUENCE.incrementAndGet();
    }

    protected static String unique(String prefix) {
        return prefix + next();
    }

    protected long client() {
        return jdbc.sql("""
                        INSERT INTO client (phone, first_name) VALUES (:phone, 'Books Test') RETURNING id
                        """)
                .param("phone", "+9198%08d".formatted(next()))
                .query(Long.class)
                .single();
    }

    protected long customer() {
        return jdbc.sql("INSERT INTO customer (name, phone) VALUES ('Books Test', :phone) RETURNING id")
                .param("phone", "+9197%08d".formatted(next()))
                .query(Long.class)
                .single();
    }

    protected long shopOrder(String total) {
        return jdbc.sql("""
                        INSERT INTO shop_order (number, client_id, order_date, delivery_date, items_rupees,
                                                total_rupees)
                        VALUES (:number, :client, CURRENT_DATE, CURRENT_DATE, CAST(:total AS numeric),
                                CAST(:total AS numeric))
                        RETURNING id
                        """)
                .param("number", unique("ORD-BT-"))
                .param("client", client())
                .param("total", total)
                .query(Long.class)
                .single();
    }

    protected long shopPayment(long order, String amount, String mode, OffsetDateTime at) {
        return jdbc.sql("""
                        INSERT INTO order_payment (order_id, amount_rupees, mode, kind, received_at)
                        VALUES (:order, CAST(:amount AS numeric), :mode, :kind, :at) RETURNING id
                        """)
                .param("order", order)
                .param("amount", amount)
                .param("mode", mode)
                .param("kind", new BigDecimal(amount).signum() < 0 ? "REFUND" : "PAYMENT")
                .param("at", at)
                .query(Long.class)
                .single();
    }

    /** An invoice for 1000 taxable + 25 CGST + 25 SGST = 1050, for a shop order. */
    protected long shopInvoice(long order, OffsetDateTime createdAt) {
        return invoice("order_id", order, createdAt);
    }

    protected long webInvoice(long webOrder, OffsetDateTime createdAt) {
        return invoice("web_order_id", webOrder, createdAt);
    }

    private long invoice(String column, long order, OffsetDateTime createdAt) {
        return jdbc.sql("""
                        INSERT INTO invoice (number, %s, issued_on, order_total_rupees, gst_rate, prices_include_tax,
                                             taxable_rupees, cgst_rupees, sgst_rupees, total_rupees, title,
                                             buyer_name, seller_name, created_at)
                        VALUES (:number, :order, CAST(CAST(:createdAt AS timestamptz) AT TIME ZONE 'Asia/Kolkata' AS date),
                                1050, 5, FALSE, 1000, 25, 25, 1050, 'Invoice', 'Buyer', 'Vaabhi', :createdAt)
                        RETURNING id
                        """.formatted(column))
                .param("number", unique("INV/BT/"))
                .param("order", order)
                .param("createdAt", createdAt)
                .query(Long.class)
                .single();
    }

    protected long webOrder(
            String method, String total, @Nullable OffsetDateTime paidAt, @Nullable OffsetDateTime refundedAt) {
        var number = unique("WEB-BT-");
        return jdbc.sql("""
                        INSERT INTO web_order (number, customer_id, status, payment_method, contact_name,
                            contact_phone, address_line1, city, state, pincode, items_rupees, shipping_rupees,
                            total_rupees, paid_at, razorpay_payment_id, refunded_rupees, refunded_at, refund_status)
                        VALUES (:number, :customer, :status, :method, 'Books Buyer', '+919876200000', '1 Street',
                            'Pune', 'Maharashtra', '411001', CAST(:total AS numeric), 0, CAST(:total AS numeric),
                            CAST(:paidAt AS timestamptz),
                            CASE WHEN CAST(:method AS text) = 'ONLINE' AND CAST(:paidAt AS text) IS NOT NULL
                                 THEN CAST(:number AS text) END,
                            CASE WHEN CAST(:refundedAt AS text) IS NOT NULL THEN CAST(:total AS numeric) END,
                            CAST(:refundedAt AS timestamptz),
                            CASE WHEN CAST(:refundedAt AS text) IS NOT NULL THEN 'PROCESSED' END)
                        RETURNING id
                        """)
                .param("number", number)
                .param("customer", customer())
                .param("status", refundedAt != null ? "CANCELLED" : "PLACED")
                .param("method", method)
                .param("total", total)
                .param("paidAt", paidAt)
                .param("refundedAt", refundedAt)
                .query(Long.class)
                .single();
    }

    protected long expense(String category, String amount, String mode) {
        return jdbc.sql("""
                        INSERT INTO expense (spent_on, category, description, amount_rupees, mode)
                        VALUES (CAST(now() AT TIME ZONE 'Asia/Kolkata' AS date), :category, 'Books test', CAST(:amount AS numeric), :mode)
                        RETURNING id
                        """)
                .param("category", category)
                .param("amount", amount)
                .param("mode", mode)
                .query(Long.class)
                .single();
    }

    protected long alterationPayment(String amount, String mode) {
        long alteration = jdbc.sql("""
                        INSERT INTO alteration (number, client_id, ready_by, paid)
                        VALUES (:number, :client, CURRENT_DATE, TRUE) RETURNING id
                        """)
                .param("number", unique("ALT-BT-"))
                .param("client", client())
                .query(Long.class)
                .single();
        return jdbc.sql("""
                        INSERT INTO alteration_payment (alteration_id, amount_rupees, mode)
                        VALUES (:alteration, CAST(:amount AS numeric), :mode) RETURNING id
                        """)
                .param("alteration", alteration)
                .param("amount", amount)
                .param("mode", mode)
                .query(Long.class)
                .single();
    }

    protected long creditNote(long invoice) {
        return jdbc.sql("""
                        INSERT INTO credit_note (number, invoice_id, reason, issued_on, title, buyer_name,
                                                 seller_name, taxable_rupees, cgst_rupees, sgst_rupees, total_rupees)
                        VALUES (:number, :invoice, 'RETURN', CAST(now() AT TIME ZONE 'Asia/Kolkata' AS date), 'Credit Note', 'Buyer', 'Vaabhi',
                                1000, 25, 25, 1050)
                        RETURNING id
                        """)
                .param("number", unique("CN/BT/"))
                .param("invoice", invoice)
                .query(Long.class)
                .single();
    }

    protected void cancelInvoice(long invoice) {
        jdbc.sql("UPDATE invoice SET cancelled_at = now() WHERE id = :id")
                .param("id", invoice)
                .update();
    }

    protected record EntryRow(
            long id,
            String step,
            String narration,
            @Nullable String orderKey) {}

    protected List<EntryRow> entries(String type, long sourceId) {
        return jdbc.sql("""
                        SELECT id, source_step AS step, narration, order_key
                        FROM journal_entry WHERE source_type = :type AND source_id = :id ORDER BY id
                        """)
                .param("type", type)
                .param("id", sourceId)
                .query(EntryRow.class)
                .list();
    }

    protected EntryRow entry(String type, long sourceId, String step) {
        var rows = entries(type, sourceId).stream()
                .filter(e -> e.step().equals(step))
                .toList();
        assertThat(rows).as("%s %d %s", type, sourceId, step).hasSize(1);
        return rows.getFirst();
    }

    /** Debit minus credit on an account (by system key) across every entry of one order. */
    protected BigDecimal net(String systemKey, String orderKey) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(l.debit_rupees - l.credit_rupees), 0)
                        FROM journal_line l
                        JOIN journal_entry e ON e.id = l.entry_id
                        JOIN account a ON a.id = l.account_id
                        WHERE a.system_key = :key AND e.order_key = :orderKey
                        """)
                .param("key", systemKey)
                .param("orderKey", orderKey)
                .query(BigDecimal.class)
                .single();
    }

    /** Debit minus credit on an account (by id) within one entry. */
    protected BigDecimal netInEntry(long entryId, long accountId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(debit_rupees - credit_rupees), 0)
                        FROM journal_line WHERE entry_id = :entry AND account_id = :account
                        """)
                .param("entry", entryId)
                .param("account", accountId)
                .query(BigDecimal.class)
                .single();
    }

    protected long accountId(String systemKey) {
        return jdbc.sql("SELECT id FROM account WHERE system_key = :key")
                .param("key", systemKey)
                .query(Long.class)
                .single();
    }
}

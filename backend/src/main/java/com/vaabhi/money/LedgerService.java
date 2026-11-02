package com.vaabhi.money;

import com.vaabhi.tailoring.PaymentMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The financial ledger (order payments and refunds, alteration payments, expenses) and what customers still owe.
 * Payments live in the tailoring module's tables; this reads them, never writes them.
 */
@Service
public class LedgerService {

    /** Longest range one ledger request may cover. */
    static final long MAX_DAYS = 400;

    /**
     * Every money movement as (at, kind, source id, reference, party, mode, signed amount). Expenses count from the
     * start of their day in the shop's time zone.
     */
    private static final String MOVEMENTS = """
            SELECT p.received_at AS at,
                   CASE WHEN p.amount_paise < 0 THEN 'REFUND' ELSE 'ORDER_PAYMENT' END AS kind,
                   o.id AS source_id, o.number AS reference,
                   c.first_name || COALESCE(' ' || c.last_name, '') AS party, p.mode, p.amount_paise
            FROM order_payment p JOIN shop_order o ON o.id = p.order_id JOIN client c ON c.id = o.client_id
            UNION ALL
            SELECT p.received_at, 'ALTERATION_PAYMENT', a.id, a.number,
                   c.first_name || COALESCE(' ' || c.last_name, ''), p.mode, p.amount_paise
            FROM alteration_payment p JOIN alteration a ON a.id = p.alteration_id JOIN client c ON c.id = a.client_id
            UNION ALL
            SELECT (e.spent_on::timestamp AT TIME ZONE :zone), 'EXPENSE', e.id, e.description, e.paid_to, e.mode,
                   -e.amount_paise
            FROM expense e
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    LedgerService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private record Movement(
            OffsetDateTime at,
            LedgerEntry.Kind kind,
            long sourceId,
            String reference,
            @Nullable String party,
            PaymentMode mode,
            long amountPaise) {}

    /** Entries from {@code from} to {@code to} (inclusive, shop time), with the opening balance before them. */
    @Transactional(readOnly = true)
    public Ledger ledger(LocalDate from, LocalDate to, LedgerEntry.@Nullable Kind kind) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end date is before the start date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a range of at most " + MAX_DAYS + " days");
        }
        ZoneId zone = clock.getZone();
        var start = from.atStartOfDay(zone).toOffsetDateTime();
        var end = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        String kindName = kind == null ? null : kind.name();
        long opening = jdbc.sql("SELECT COALESCE(SUM(amount_paise), 0) FROM (" + MOVEMENTS + ") m WHERE m.at < :start"
                        + " AND (CAST(:kind AS varchar) IS NULL OR m.kind = :kind)")
                .param("zone", zone.getId())
                .param("start", start)
                .param("kind", kindName)
                .query(Long.class)
                .single();
        var movements = jdbc.sql("SELECT * FROM (" + MOVEMENTS + ") m WHERE m.at >= :start AND m.at < :end"
                        + " AND (CAST(:kind AS varchar) IS NULL OR m.kind = :kind) ORDER BY m.at, m.kind, m.source_id")
                .param("zone", zone.getId())
                .param("start", start)
                .param("end", end)
                .param("kind", kindName)
                .query(Movement.class)
                .list();

        long balance = opening;
        long in = 0;
        long out = 0;
        var byMode = new EnumMap<PaymentMode, long[]>(PaymentMode.class);
        var entries = new ArrayList<LedgerEntry>(movements.size());
        for (var m : movements) {
            balance += m.amountPaise();
            var totals = byMode.computeIfAbsent(m.mode(), mode -> new long[2]);
            if (m.amountPaise() >= 0) {
                in += m.amountPaise();
                totals[0] += m.amountPaise();
            } else {
                out -= m.amountPaise();
                totals[1] -= m.amountPaise();
            }
            entries.add(new LedgerEntry(
                    m.at(), m.kind(), m.sourceId(), m.reference(), m.party(), m.mode(), m.amountPaise(), balance));
        }
        List<ModeTotal> modes = byMode.entrySet().stream()
                .map(e -> new ModeTotal(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
        return new Ledger(from, to, opening, in, out, opening + in - out, modes, entries);
    }

    private record DueRow(
            Due.Kind kind,
            long id,
            String number,
            long clientId,
            String firstName,
            @Nullable String lastName,
            String clientPhone,
            LocalDate date,
            long totalPaise,
            long paidPaise,
            boolean handedOver,
            @Nullable String invoiceNumber) {}

    /**
     * Orders (not cancelled) and paid alterations (not cancelled) with money still owed, oldest first. An invoiced
     * order owes its invoice total (which includes GST added on top), otherwise its order total.
     */
    @Transactional(readOnly = true)
    public Outstanding outstanding(@Nullable Long clientId) {
        var today = LocalDate.now(clock);
        var rows = jdbc.sql("""
                        SELECT * FROM (
                            SELECT 'ORDER' AS kind, o.id, o.number, c.id AS client_id, c.first_name, c.last_name,
                                   c.phone AS client_phone, o.delivery_date AS date,
                                   COALESCE(v.total_paise, o.total_paise) AS total_paise, o.paid_paise,
                                   o.status = 'DELIVERED' AS handed_over, v.number AS invoice_number
                            FROM shop_order o JOIN client c ON c.id = o.client_id
                            LEFT JOIN invoice v ON v.order_id = o.id AND v.cancelled_at IS NULL
                            WHERE o.status <> 'CANCELLED'
                            UNION ALL
                            SELECT 'ALTERATION', a.id, a.number, c.id, c.first_name, c.last_name, c.phone,
                                   a.ready_by, a.total_paise, a.collected_paise, a.status = 'DELIVERED', NULL
                            FROM alteration a JOIN client c ON c.id = a.client_id
                            WHERE a.status <> 'CANCELLED' AND a.paid
                        ) d
                        WHERE d.total_paise > d.paid_paise
                          AND (CAST(:client AS bigint) IS NULL OR d.client_id = :client)
                        ORDER BY d.date, d.kind, d.id
                        """).param("client", clientId).query(DueRow.class).list();
        long total = 0;
        long handedOver = 0;
        var items = new ArrayList<Due>(rows.size());
        for (var row : rows) {
            long due = row.totalPaise() - row.paidPaise();
            total += due;
            if (row.handedOver()) {
                handedOver += due;
            }
            items.add(new Due(
                    row.kind(),
                    row.id(),
                    row.number(),
                    row.clientId(),
                    row.lastName() == null || row.lastName().isBlank()
                            ? row.firstName()
                            : row.firstName() + " " + row.lastName(),
                    row.clientPhone(),
                    row.date(),
                    row.totalPaise(),
                    row.paidPaise(),
                    due,
                    (int) Math.max(0, ChronoUnit.DAYS.between(row.date(), today)),
                    row.handedOver(),
                    row.invoiceNumber()));
        }
        return new Outstanding(total, handedOver, items);
    }
}

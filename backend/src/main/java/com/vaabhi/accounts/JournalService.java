package com.vaabhi.accounts;

import com.vaabhi.settings.NumberSequence;
import com.vaabhi.settings.NumberingService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The journal: posting, reversing, listing, account ledgers and the trial balance. */
@Service
public class JournalService {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final Set<String> REVERSIBLE = Set.of("MANUAL", "OPENING", "BANK_LINE", "YEAR_END");
    private static final int MAX_NARRATION = 200;
    private static final int MAX_MEMO = 120;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ID = "id";

    private final JdbcClient jdbc;
    private final NumberingService numbering;
    private final Clock clock;

    JournalService(JdbcClient jdbc, NumberingService numbering, Clock clock) {
        this.jdbc = jdbc;
        this.numbering = numbering;
        this.clock = clock;
    }

    record AccountRow(long id, String code, String name, boolean active) {}

    record AccountHead(String code, String name, AccountType type) {}

    record EntryRow(
            long id,
            String number,
            LocalDate entryDate,
            String sourceType,
            @Nullable Long sourceId,
            String sourceStep,
            String narration,
            @Nullable String orderKey,
            @Nullable Long reversesId,
            @Nullable Long reversedById,
            @Nullable String createdBy,
            OffsetDateTime createdAt,
            @Nullable String sourceLabel,
            @Nullable Long orderId,
            @Nullable Long alterationId,
            @Nullable String orderNumber) {}

    record LedgerRow(
            long entryId,
            String number,
            LocalDate entryDate,
            String narration,
            @Nullable String partyName,
            @Nullable String memo,
            BigDecimal debitRupees,
            BigDecimal creditRupees) {}

    record TrialRow(long id, String code, String name, AccountType type, BigDecimal debits, BigDecimal credits) {}

    /** Posts an entry in the caller's transaction (or its own), taking the next JV number. */
    @Transactional(propagation = Propagation.REQUIRED)
    public JournalEntry post(NewEntry entry, @Nullable String actor) {
        validate(entry);
        long id = jdbc.sql("""
                        INSERT INTO journal_entry (number, entry_date, source_type, source_id, source_step, narration,
                                                   order_key, reverses_id, created_by)
                        VALUES (:number, :date, :sourceType, :sourceId, :sourceStep, :narration, :orderKey,
                                :reversesId, :by)
                        RETURNING id
                        """)
                .param("number", numbering.next(NumberSequence.Kind.JOURNAL))
                .param("date", entry.date())
                .param("sourceType", entry.sourceType())
                .param("sourceId", entry.sourceId())
                .param("sourceStep", entry.sourceStep())
                .param("narration", abbreviate(entry.narration(), MAX_NARRATION))
                .param("orderKey", entry.orderKey())
                .param("reversesId", entry.reversesId())
                .param("by", actor)
                .query(Long.class)
                .single();
        int position = 1;
        for (var line : entry.lines()) {
            jdbc.sql("""
                            INSERT INTO journal_line (entry_id, position, account_id, party_id, debit_rupees,
                                                      credit_rupees, memo)
                            VALUES (:entry, :position, :account, :party, :debit, :credit, :memo)
                            """)
                    .param("entry", id)
                    .param("position", position++)
                    .param("account", line.accountId())
                    .param("party", line.partyId())
                    .param("debit", Rupees.of(line.debitRupees()))
                    .param("credit", Rupees.of(line.creditRupees()))
                    .param("memo", line.memo() == null ? null : abbreviate(line.memo(), MAX_MEMO))
                    .update();
        }
        return get(id);
    }

    /** Posts the opposite of a manual, opening or bank line entry, dated today. */
    @Transactional
    public JournalEntry reverse(long id, @Nullable String actor) {
        return reverseOn(id, LocalDate.now(clock.withZone(SHOP_ZONE)), actor);
    }

    /** Like {@link #reverse}, but dated {@code date}; a locked month refuses with 409. */
    @Transactional
    public JournalEntry reverseOn(long id, LocalDate date, @Nullable String actor) {
        var original = get(id);
        if (!REVERSIBLE.contains(original.sourceType())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only manual, opening and bank line entries can be reversed");
        }
        if ("REVERSAL".equals(original.sourceStep())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A reversal can't be reversed");
        }
        if (original.reversedById() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This entry is already reversed");
        }
        var swapped = original.lines().stream()
                .map(line -> new NewEntryLine(
                        line.accountId(), line.partyId(), line.creditRupees(), line.debitRupees(), line.memo()))
                .toList();
        return post(
                new NewEntry(
                        date,
                        original.sourceType(),
                        null,
                        "REVERSAL",
                        "Reversal of " + original.number(),
                        null,
                        id,
                        swapped),
                actor);
    }

    @Transactional(readOnly = true)
    public JournalEntry get(long id) {
        var row = jdbc.sql("""
                        SELECT e.id, e.number, e.entry_date, e.source_type, e.source_id, e.source_step, e.narration,
                               e.order_key, e.reverses_id, e.created_by, e.created_at,
                               (SELECT r.id FROM journal_entry r WHERE r.reverses_id = e.id) AS reversed_by_id,
                               CASE e.source_type
                                   WHEN 'INVOICE' THEN (SELECT number FROM invoice WHERE id = e.source_id)
                                   WHEN 'CREDIT_NOTE' THEN (SELECT number FROM credit_note WHERE id = e.source_id)
                                   WHEN 'ORDER_PAYMENT' THEN (SELECT o.number FROM order_payment p
                                                              JOIN shop_order o ON o.id = p.order_id
                                                              WHERE p.id = e.source_id)
                                   WHEN 'ALTERATION_PAYMENT' THEN (SELECT a.number FROM alteration_payment p
                                                                   JOIN alteration a ON a.id = p.alteration_id
                                                                   WHERE p.id = e.source_id)
                                   WHEN 'WEB_ORDER' THEN (SELECT number FROM web_order WHERE id = e.source_id)
                                   WHEN 'WEB_RETURN' THEN (SELECT number FROM web_order_return WHERE id = e.source_id)
                                   WHEN 'EXPENSE' THEN (SELECT description FROM expense WHERE id = e.source_id)
                               END AS source_label,
                               CASE e.source_type
                                   WHEN 'ORDER_PAYMENT' THEN (SELECT p.order_id FROM order_payment p
                                                              WHERE p.id = e.source_id)
                               END AS order_id,
                               CASE e.source_type
                                   WHEN 'ALTERATION_PAYMENT' THEN (SELECT p.alteration_id FROM alteration_payment p
                                                                   WHERE p.id = e.source_id)
                               END AS alteration_id,
                               CASE e.source_type
                                   WHEN 'WEB_ORDER' THEN (SELECT number FROM web_order WHERE id = e.source_id)
                                   WHEN 'WEB_RETURN' THEN (SELECT w.number FROM web_order_return r
                                                           JOIN web_order w ON w.id = r.order_id
                                                           WHERE r.id = e.source_id)
                               END AS order_number
                        FROM journal_entry e WHERE e.id = :id
                        """)
                .param(ID, id)
                .query(EntryRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal entry not found"));
        var lines = jdbc.sql("""
                        SELECT l.account_id, a.code AS account_code, a.name AS account_name, l.party_id,
                               p.name AS party_name, l.debit_rupees, l.credit_rupees, l.memo
                        FROM journal_line l
                        JOIN account a ON a.id = l.account_id
                        LEFT JOIN party p ON p.id = l.party_id
                        WHERE l.entry_id = :id
                        ORDER BY l.position
                        """).param(ID, id).query(JournalEntryLine.class).list();
        var sourceId = row.sourceId();
        var sourceLabel = row.sourceLabel();
        var link = sourceId == null || sourceLabel == null
                ? null
                : new JournalSourceLink(
                        row.sourceType(), sourceId, sourceLabel, row.orderId(), row.alterationId(), row.orderNumber());
        return new JournalEntry(
                row.id(),
                row.number(),
                row.entryDate(),
                row.sourceType(),
                row.sourceId(),
                row.sourceStep(),
                row.narration(),
                row.orderKey(),
                row.reversesId(),
                row.reversedById(),
                row.createdBy(),
                row.createdAt(),
                link,
                lines);
    }

    @Transactional(readOnly = true)
    public JournalPage list(@Nullable String q, @Nullable LocalDate from, @Nullable LocalDate to, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        var like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        String where = """
                WHERE (CAST(:q AS varchar) IS NULL OR lower(e.number) LIKE :q OR lower(e.narration) LIKE :q)
                  AND (CAST(:from AS date) IS NULL OR e.entry_date >= :from)
                  AND (CAST(:to AS date) IS NULL OR e.entry_date <= :to)
                """;
        long total = jdbc.sql("SELECT COUNT(*) FROM journal_entry e " + where)
                .param("q", like)
                .param("from", from)
                .param("to", to)
                .query(Long.class)
                .single();
        var items = jdbc.sql("""
                        SELECT e.id, e.number, e.entry_date AS date, e.narration, e.source_type, e.source_step,
                               (SELECT COALESCE(SUM(l.debit_rupees), 0) FROM journal_line l
                                WHERE l.entry_id = e.id) AS total_rupees,
                               EXISTS (SELECT 1 FROM journal_entry r WHERE r.reverses_id = e.id) AS reversed
                        FROM journal_entry e
                        """ + where + """
                        ORDER BY e.entry_date DESC, e.id DESC LIMIT :limit OFFSET :offset
                        """)
                .param("q", like)
                .param("from", from)
                .param("to", to)
                .param("limit", safeSize)
                .param("offset", (safePage - 1) * safeSize)
                .query(JournalListItem.class)
                .list();
        return new JournalPage(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public LedgerStatement ledger(long accountId, LocalDate from, LocalDate to) {
        var account = jdbc.sql("SELECT code, name, type FROM account WHERE id = :id")
                .param(ID, accountId)
                .query(AccountHead.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        var type = account.type();
        var before = jdbc.sql("""
                        SELECT COALESCE(SUM(l.debit_rupees), 0) - COALESCE(SUM(l.credit_rupees), 0)
                        FROM journal_line l JOIN journal_entry e ON e.id = l.entry_id
                        WHERE l.account_id = :account AND e.entry_date < :from
                        """)
                .param("account", accountId)
                .param("from", from)
                .query(BigDecimal.class)
                .single();
        var rows = jdbc.sql("""
                        SELECT e.id AS entry_id, e.number, e.entry_date, e.narration, p.name AS party_name, l.memo,
                               l.debit_rupees, l.credit_rupees
                        FROM journal_line l
                        JOIN journal_entry e ON e.id = l.entry_id
                        LEFT JOIN party p ON p.id = l.party_id
                        WHERE l.account_id = :account AND e.entry_date BETWEEN :from AND :to
                        ORDER BY e.entry_date, e.id, l.position
                        """)
                .param("account", accountId)
                .param("from", from)
                .param("to", to)
                .query(LedgerRow.class)
                .list();
        var opening = Rupees.of(signed(type, before));
        var running = opening;
        var debits = Rupees.ZERO;
        var credits = Rupees.ZERO;
        var lines = new ArrayList<LedgerStatementLine>();
        for (var row : rows) {
            running = Rupees.of(running.add(signed(type, row.debitRupees().subtract(row.creditRupees()))));
            debits = debits.add(row.debitRupees());
            credits = credits.add(row.creditRupees());
            lines.add(new LedgerStatementLine(
                    row.entryId(),
                    row.number(),
                    row.entryDate(),
                    row.narration(),
                    row.partyName(),
                    row.memo(),
                    row.debitRupees(),
                    row.creditRupees(),
                    running));
        }
        return new LedgerStatement(
                accountId,
                account.code(),
                account.name(),
                type,
                from,
                to,
                opening,
                Rupees.of(debits),
                Rupees.of(credits),
                running,
                lines);
    }

    @Transactional(readOnly = true)
    public TrialBalance trialBalance(LocalDate asOf) {
        var rows = jdbc.sql("""
                        SELECT a.id, a.code, a.name, a.type, SUM(l.debit_rupees) AS debits,
                               SUM(l.credit_rupees) AS credits
                        FROM account a
                        JOIN journal_line l ON l.account_id = a.id
                        JOIN journal_entry e ON e.id = l.entry_id
                        WHERE e.entry_date <= :asOf
                        GROUP BY a.id
                        HAVING SUM(l.debit_rupees) <> SUM(l.credit_rupees)
                        ORDER BY a.code
                        """).param("asOf", asOf).query(TrialRow.class).list();
        var lines = new ArrayList<TrialBalanceLine>();
        var totalDebit = Rupees.ZERO;
        var totalCredit = Rupees.ZERO;
        for (var row : rows) {
            var net = row.debits().subtract(row.credits());
            var debit = net.signum() > 0 ? Rupees.of(net) : Rupees.ZERO;
            var credit = net.signum() < 0 ? Rupees.of(net.negate()) : Rupees.ZERO;
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            lines.add(new TrialBalanceLine(row.id(), row.code(), row.name(), row.type(), debit, credit));
        }
        return new TrialBalance(asOf, lines, totalDebit, totalCredit, totalDebit.compareTo(totalCredit) == 0);
    }

    private void validate(NewEntry entry) {
        if (entry.lines().size() < 2) {
            throw bad("Add at least two lines");
        }
        var debits = Rupees.ZERO;
        var credits = Rupees.ZERO;
        for (var line : entry.lines()) {
            var debit = Rupees.of(line.debitRupees());
            var credit = Rupees.of(line.creditRupees());
            if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() > 0) == (credit.signum() > 0)) {
                throw bad("Each line needs a debit or a credit, not both");
            }
            debits = debits.add(debit);
            credits = credits.add(credit);
        }
        if (debits.compareTo(credits) != 0) {
            throw bad("Debits and credits must be equal");
        }
        var ids = entry.lines().stream().map(NewEntryLine::accountId).distinct().toList();
        Map<Long, AccountRow> accounts = jdbc
                .sql("SELECT id, code, name, active FROM account WHERE id IN (:ids)")
                .param("ids", ids)
                .query(AccountRow.class)
                .list()
                .stream()
                .collect(Collectors.toMap(AccountRow::id, Function.identity()));
        for (long accountId : ids) {
            var account = accounts.get(accountId);
            if (account == null) {
                throw bad("Account " + accountId + " not found");
            }
            if (!account.active()) {
                throw bad(account.code().trim() + " " + account.name() + " is turned off");
            }
        }
        boolean locked = jdbc.sql("SELECT EXISTS (SELECT 1 FROM period_lock WHERE month = :month)")
                .param("month", entry.date().withDayOfMonth(1))
                .query(Boolean.class)
                .single();
        if (locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MONTH_YEAR.format(entry.date()) + " is locked");
        }
    }

    private static BigDecimal signed(AccountType type, BigDecimal debitMinusCredit) {
        return type.debitNormal() ? debitMinusCredit : debitMinusCredit.negate();
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

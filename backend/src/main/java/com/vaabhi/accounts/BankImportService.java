package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Imports a bank statement file: preview, saved mapping, dedupe, and the statement list. */
@Service
public class BankImportService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int PREVIEW_ROWS = 20;
    private static final int FILE_NAME_MAX = 200;
    private static final String KIND = "BANK";
    private static final String SUMMARY_SQL = """
            SELECT s.id, s.file_name, s.period_from, s.period_to, s.opening_rupees, s.closing_rupees, s.line_count,
                   (SELECT COUNT(*) FROM bank_line l WHERE l.statement_id = s.id AND l.status = 'UNMATCHED')
                       AS unmatched_count,
                   s.uploaded_at
            FROM bank_statement s
            """;

    private final JdbcClient jdbc;
    private final ChartService chart;
    private final ImportProfileService profiles;
    private final BankMatcher matcher;

    BankImportService(JdbcClient jdbc, ChartService chart, ImportProfileService profiles, BankMatcher matcher) {
        this.jdbc = jdbc;
        this.chart = chart;
        this.profiles = profiles;
        this.matcher = matcher;
    }

    /** The first rows of the file, so the owner can check the column mapping before importing. */
    @Transactional(readOnly = true)
    public FilePreview preview(String fileName, byte[] bytes) {
        checkSize(bytes);
        var rows = TabularFile.read(fileName, new ByteArrayInputStream(bytes));
        return new FilePreview(
                rows.size() > PREVIEW_ROWS ? rows.subList(0, PREVIEW_ROWS) : rows, profiles.latest(KIND));
    }

    /** Saves the profile, the statement and its new lines, then auto-matches them. */
    @Transactional
    public BankImportSummary importStatement(
            String fileName,
            byte[] bytes,
            ColumnMapping mapping,
            @Nullable BigDecimal opening,
            @Nullable BigDecimal closing,
            @Nullable String actor) {
        checkSize(bytes);
        var parsed = BankFileParser.parse(TabularFile.read(fileName, new ByteArrayInputStream(bytes)), mapping);
        var lines = parsed.lines();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "No rows with a valid date were found. Check the header row and date format.");
        }
        profiles.save(KIND, mapping);
        long accountId = chart.idOf("BANK");
        LocalDate periodFrom = lines.stream().map(ParsedBankLine::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate periodTo = lines.stream().map(ParsedBankLine::date).max(LocalDate::compareTo).orElseThrow();
        var openingRupees = openingRupees(opening, lines);
        var closingRupees = closing == null ? lines.getLast().balance() : Rupees.of(closing);
        long statementId = jdbc.sql("""
                        INSERT INTO bank_statement (account_id, file_name, period_from, period_to, opening_rupees,
                                                    closing_rupees, uploaded_by)
                        VALUES (:account, :fileName, :from, :to, :opening, :closing, :by)
                        RETURNING id
                        """)
                .param("account", accountId)
                .param("fileName", fileName(fileName))
                .param("from", periodFrom)
                .param("to", periodTo)
                .param("opening", openingRupees)
                .param("closing", closingRupees)
                .param("by", actor)
                .query(Long.class)
                .single();
        int added = 0;
        for (var line : lines) {
            added += jdbc.sql("""
                            INSERT INTO bank_line (statement_id, account_id, line_date, description, reference,
                                                   debit_rupees, credit_rupees, balance_rupees, dedupe_key)
                            VALUES (:statement, :account, :date, :description, :reference, :debit, :credit,
                                    :balance, :key)
                            ON CONFLICT (account_id, dedupe_key) DO NOTHING
                            """)
                    .param("statement", statementId)
                    .param("account", accountId)
                    .param("date", line.date())
                    .param("description", line.description())
                    .param("reference", line.reference())
                    .param("debit", line.debit())
                    .param("credit", line.credit())
                    .param("balance", line.balance())
                    .param("key", dedupeKey(line))
                    .update();
        }
        jdbc.sql("UPDATE bank_statement SET line_count = :count WHERE id = :id")
                .param("count", added)
                .param("id", statementId)
                .update();
        int autoMatched = matcher.autoMatch(statementId);
        return new BankImportSummary(statementId, added, lines.size() - added, autoMatched, parsed.skipped());
    }

    /** Every statement, newest first. */
    @Transactional(readOnly = true)
    public List<BankStatementSummary> list() {
        return jdbc.sql(SUMMARY_SQL + " ORDER BY s.uploaded_at DESC, s.id DESC")
                .query(BankStatementSummary.class)
                .list();
    }

    /** One statement and its lines, optionally only those with {@code status}. */
    @Transactional(readOnly = true)
    public BankStatementDetail detail(long id, @Nullable String status) {
        var statement = jdbc.sql(SUMMARY_SQL + " WHERE s.id = :id")
                .param("id", id)
                .query(BankStatementSummary.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));
        var query = jdbc.sql("""
                        SELECT l.id, l.line_date AS date, l.description, l.reference, l.debit_rupees,
                               l.credit_rupees, l.balance_rupees, l.status, l.journal_entry_id,
                               j.number AS journal_number, l.ignore_reason
                        FROM bank_line l
                        LEFT JOIN journal_entry j ON j.id = l.journal_entry_id
                        WHERE l.statement_id = :id
                        """ + (status == null ? "" : "AND l.status = :status\n") + """
                        ORDER BY l.line_date, l.id
                        """)
                .param("id", id);
        if (status != null) {
            query = query.param("status", status);
        }
        return new BankStatementDetail(statement, query.query(BankLineView.class).list());
    }

    /** One line with its journal entry number; 404 when it does not exist. */
    @Transactional(readOnly = true)
    BankLineView line(long lineId) {
        return jdbc.sql("""
                        SELECT l.id, l.line_date AS date, l.description, l.reference, l.debit_rupees,
                               l.credit_rupees, l.balance_rupees, l.status, l.journal_entry_id,
                               j.number AS journal_number, l.ignore_reason
                        FROM bank_line l
                        LEFT JOIN journal_entry j ON j.id = l.journal_entry_id
                        WHERE l.id = :id
                        """)
                .param("id", lineId)
                .query(BankLineView.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found"));
    }

    private static void checkSize(byte[] bytes) {
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("File is larger than 5 MB");
        }
    }

    /** The opening the owner gave, else the first line's balance less its movement when it is the first line. */
    private static @Nullable BigDecimal openingRupees(@Nullable BigDecimal opening, List<ParsedBankLine> lines) {
        if (opening != null) {
            return Rupees.of(opening);
        }
        var first = lines.getFirst();
        if (first.balance() == null) {
            return null;
        }
        return Rupees.of(first.balance().subtract(first.credit()).add(first.debit()));
    }

    private static String fileName(String fileName) {
        return fileName.length() > FILE_NAME_MAX ? fileName.substring(0, FILE_NAME_MAX) : fileName;
    }

    /** SHA-256 of the line's text, so an overlapping upload adds nothing. */
    private static String dedupeKey(ParsedBankLine line) {
        String text = String.join(
                "|",
                line.date().toString(),
                line.debit().toPlainString(),
                line.credit().toPlainString(),
                line.reference() == null ? "" : line.reference(),
                line.balance() == null ? "" : line.balance().toPlainString(),
                line.description());
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

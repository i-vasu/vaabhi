package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A statement's reconciliation report and its CSV download. */
@Service
@Transactional(readOnly = true)
public class BankReconciliationService {

    /** Bank journal lines in the period that no bank line links to, so only the ledger knows them. */
    private static final String LEDGER_ONLY_SQL = """
            SELECT e.id AS journal_entry_id, e.number, e.entry_date AS date, e.narration,
                   l.debit_rupees, l.credit_rupees
            FROM journal_line l
            JOIN journal_entry e ON e.id = l.entry_id
            JOIN account a ON a.id = l.account_id
            WHERE a.system_key = 'BANK'
              AND e.entry_date BETWEEN :from AND :to
              AND e.reverses_id IS NULL
              AND NOT EXISTS (SELECT 1 FROM journal_entry r WHERE r.reverses_id = e.id)
              AND NOT EXISTS (SELECT 1 FROM bank_line b WHERE b.journal_entry_id = e.id)
              AND NOT EXISTS (SELECT 1 FROM bank_line b WHERE b.difference_entry_id = e.id)
            ORDER BY e.entry_date, e.id, l.position
            """;

    /** The earliest Razorpay clearing debit after the last settlement, i.e. money not yet paid out. */
    private static final String OLDEST_UNSETTLED_SQL = """
            SELECT MIN(e.entry_date) AS date
            FROM journal_entry e
            WHERE EXISTS (SELECT 1 FROM journal_line l JOIN account a ON a.id = l.account_id
                          WHERE l.entry_id = e.id AND a.system_key = 'RAZORPAY_CLEARING'
                            AND l.debit_rupees > 0)
              AND e.entry_date <= :to
              AND e.entry_date > COALESCE(
                  (SELECT MAX(s.entry_date) FROM journal_entry s WHERE s.source_type = 'RAZORPAY_SETTLEMENT'),
                  DATE '1900-01-01')
              AND e.reverses_id IS NULL
              AND NOT EXISTS (SELECT 1 FROM journal_entry r WHERE r.reverses_id = e.id)
            """;

    private final JdbcClient jdbc;
    private final BankImportService imports;
    private final JournalService journal;
    private final ChartService chart;

    BankReconciliationService(JdbcClient jdbc, BankImportService imports, JournalService journal, ChartService chart) {
        this.jdbc = jdbc;
        this.imports = imports;
        this.journal = journal;
        this.chart = chart;
    }

    record OldestUnsettled(@Nullable LocalDate date) {}

    /** The statement's closing against the ledger Bank balance at its last day, and everything that explains it. */
    public BankReconciliation report(long statementId) {
        var detail = imports.detail(statementId, null);
        var statement = detail.statement();
        var periodTo = statement.periodTo();
        var ledgerBalance =
                journal.ledger(chart.idOf("BANK"), periodTo, periodTo).closingRupees();
        var closing = statement.closingRupees();
        var difference = closing == null ? null : Rupees.of(closing.subtract(ledgerBalance));
        var unmatched = detail.lines().stream()
                .filter(line -> "UNMATCHED".equals(line.status()))
                .toList();
        var ignored = detail.lines().stream()
                .filter(line -> "IGNORED".equals(line.status()))
                .toList();
        var ledgerOnly = jdbc.sql(LEDGER_ONLY_SQL)
                .param("from", statement.periodFrom())
                .param("to", periodTo)
                .query(LedgerOnlyMovement.class)
                .list();
        var razorpayClearing = journal.ledger(chart.idOf("RAZORPAY_CLEARING"), periodTo, periodTo)
                .closingRupees();
        var codClearing =
                journal.ledger(chart.idOf("COD_CLEARING"), periodTo, periodTo).closingRupees();
        var oldestRow = jdbc.sql(OLDEST_UNSETTLED_SQL)
                .param("to", periodTo)
                .query(OldestUnsettled.class)
                .single();
        var oldest = oldestRow.date();
        boolean openingMismatch =
                difference != null && difference.signum() != 0 && unmatched.isEmpty() && ledgerOnly.isEmpty();
        return new BankReconciliation(
                statement,
                closing,
                ledgerBalance,
                difference,
                unmatched,
                ledgerOnly,
                razorpayClearing,
                codClearing,
                oldest,
                openingMismatch,
                ignored);
    }

    /** The report as a CSV the owner can open in a spreadsheet. */
    public String csv(long statementId) {
        var result = report(statementId);
        var writer = new StringWriter();
        try (var printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().build())) {
            printer.printRecord("Section", "Date", "Reference", "Description", "Debit", "Credit");
            for (var line : result.unmatchedLines()) {
                printLine(
                        printer,
                        "Unmatched bank line",
                        line.date(),
                        line.reference(),
                        line.description(),
                        line.debitRupees(),
                        line.creditRupees());
            }
            for (var line : result.ignoredLines()) {
                var reason = line.ignoreReason();
                var description = reason == null || reason.isBlank()
                        ? line.description()
                        : line.description() + " (" + reason + ")";
                printLine(
                        printer,
                        "Ignored bank line",
                        line.date(),
                        line.reference(),
                        description,
                        line.debitRupees(),
                        line.creditRupees());
            }
            for (var movement : result.ledgerOnly()) {
                printLine(
                        printer,
                        "Ledger only",
                        movement.date(),
                        movement.number(),
                        movement.narration(),
                        movement.debitRupees(),
                        movement.creditRupees());
            }
            printer.println();
            summaryRow(printer, "Statement closing", result.statementClosingRupees());
            summaryRow(printer, "Ledger balance", result.ledgerBalanceRupees());
            summaryRow(printer, "Difference", result.differenceRupees());
            summaryRow(printer, "Razorpay clearing", result.razorpayClearingRupees());
            summaryRow(printer, "COD clearing", result.codClearingRupees());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    private static void printLine(
            CSVPrinter printer,
            String section,
            LocalDate date,
            @Nullable String reference,
            String description,
            BigDecimal debit,
            BigDecimal credit)
            throws IOException {
        printer.printRecord(
                section,
                date,
                reference == null ? "" : reference,
                description,
                debit.toPlainString(),
                credit.toPlainString());
    }

    private static void summaryRow(CSVPrinter printer, String label, @Nullable BigDecimal amount) throws IOException {
        printer.printRecord("Summary", "", "", label, amount == null ? "" : amount.toPlainString(), "");
    }
}

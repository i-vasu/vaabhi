package com.vaabhi.accounts;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The per-statement reconciliation report: closing against the ledger, the explanations and the CSV. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class BankReconciliationIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String HEADER = "Date,Narration,Ref,Debit,Credit,Balance";

    @Autowired
    private JournalService journal;

    @Autowired
    private BankImportService imports;

    @Autowired
    private BankReconciliationService reconciliation;

    /** A day in the current books month, counting from its first day. */
    private static LocalDate day(int offset) {
        return LocalDate.now(IST).withDayOfMonth(1).plusDays(offset);
    }

    /** An amount no other test uses, so auto-matching cannot pick up their Bank movements. */
    private static BigDecimal amount() {
        return new BigDecimal(900000 + next() * 7).movePointLeft(2);
    }

    /** A description no earlier test or run used, so the account's dedupe keys never collide with them. */
    private static String description() {
        return unique("RECON ") + "-" + System.nanoTime();
    }

    private static ColumnMapping mapping() {
        return new ColumnMapping(
                0,
                Map.of("date", 0, "description", 1, "reference", 2, "debit", 3, "credit", 4, "balance", 5),
                "yyyy-MM-dd",
                AmountStyle.DEBIT_CREDIT);
    }

    private static String row(
            String date,
            String narration,
            String reference,
            @Nullable BigDecimal debit,
            @Nullable BigDecimal credit,
            String balance) {
        return String.join(
                ",",
                date,
                narration,
                reference,
                debit == null ? "" : debit.toPlainString(),
                credit == null ? "" : credit.toPlainString(),
                balance);
    }

    private static byte[] csv(String... rows) {
        return (HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(UTF_8);
    }

    private long receipt(LocalDate date, BigDecimal amount) {
        return journal.post(
                        new NewEntry(
                                date,
                                "MANUAL",
                                null,
                                "MANUAL",
                                "Test receipt " + next(),
                                null,
                                null,
                                List.of(
                                        new NewEntryLine(accountId("BANK"), null, amount, Rupees.ZERO, null),
                                        new NewEntryLine(accountId("SALES"), null, Rupees.ZERO, amount, null))),
                        null)
                .id();
    }

    @Test
    void zeroDifferenceWhenAllMatched() {
        var amount = amount();
        var date = day(2);
        long receipt = receipt(date, amount);
        var closing = journal.ledger(accountId("BANK"), date, date).closingRupees();

        var summary = imports.importStatement(
                "recon.csv",
                csv(row(date.toString(), description(), "", null, amount, "")),
                mapping(),
                null,
                closing,
                null);

        var report = reconciliation.report(summary.statementId());
        assertThat(report.differenceRupees()).isEqualByComparingTo("0.00");
        assertThat(report.unmatchedLines()).isEmpty();
        var lines = imports.detail(summary.statementId(), null).lines();
        assertThat(lines.getFirst().journalEntryId()).isEqualTo(receipt);
    }

    @Test
    void unmatchedLineExplainsDifference() {
        var amount = amount();
        var date = day(3);
        var closing =
                journal.ledger(accountId("BANK"), date, date).closingRupees().add(amount);

        var summary = imports.importStatement(
                "recon.csv",
                csv(row(date.toString(), description(), "", null, amount, "")),
                mapping(),
                null,
                closing,
                null);

        var report = reconciliation.report(summary.statementId());
        assertThat(report.differenceRupees()).isEqualByComparingTo(amount);
        assertThat(report.unmatchedLines()).hasSize(1);
        assertThat(report.unmatchedLines().getFirst().creditRupees()).isEqualByComparingTo(amount);
        assertThat(report.openingMismatch()).isFalse();
    }

    @Test
    void ledgerOnlyListsUnlinkedReceipt() {
        var date = day(4);
        long receipt = receipt(date, amount());
        var unrelated = amount();

        var summary = imports.importStatement(
                "recon.csv",
                csv(row(date.toString(), description(), "", null, unrelated, "")),
                mapping(),
                null,
                null,
                null);

        var report = reconciliation.report(summary.statementId());
        assertThat(report.ledgerOnly())
                .extracting(LedgerOnlyMovement::journalEntryId)
                .contains(receipt);
    }

    @Test
    void csvHasHeaderAndSummary() {
        var date = day(5);
        var summary = imports.importStatement(
                "recon.csv",
                csv(row(date.toString(), description(), "", null, amount(), "")),
                mapping(),
                null,
                null,
                null);

        var text = reconciliation.csv(summary.statementId());
        assertThat(text).startsWith("Section,Date,Reference,Description,Debit,Credit");
        assertThat(text).contains("Ledger balance");
    }
}

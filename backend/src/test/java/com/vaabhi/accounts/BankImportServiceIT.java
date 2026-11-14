package com.vaabhi.accounts;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Bank statement import: saved profile, dedupe across uploads, balances and rejected files. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class BankImportServiceIT extends BooksFixtures {

    private static final String HEADER = "Date,Narration,Ref,Debit,Credit,Balance";

    @Autowired
    private BankImportService imports;

    @Autowired
    private ImportProfileService profiles;

    private static ColumnMapping mapping() {
        return new ColumnMapping(
                0,
                Map.of("date", 0, "description", 1, "reference", 2, "debit", 3, "credit", 4, "balance", 5),
                "yyyy-MM-dd",
                AmountStyle.DEBIT_CREDIT);
    }

    /** A description no earlier test or run used, so the account's dedupe keys never collide with them. */
    private static String description() {
        return unique("UPI ") + "-" + System.nanoTime();
    }

    private static String row(
            String date, String narration, String reference, String debit, String credit, String balance) {
        return String.join(",", date, narration, reference, debit, credit, balance);
    }

    private static byte[] csv(String... rows) {
        return (HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(UTF_8);
    }

    @Test
    void importsAndSavesProfile() {
        var mapping = mapping();
        var summary = imports.importStatement(
                "s.csv",
                csv(
                        row("2026-02-01", description(), "REF-1", "", "100.00", "1100.00"),
                        row("2026-02-02", description(), "REF-2", "50.00", "", "1050.00"),
                        row("2026-02-03", description(), "REF-3", "", "25.00", "1075.00")),
                mapping,
                null,
                null,
                null);
        assertThat(summary.statementId()).isPositive();
        assertThat(summary.added()).isEqualTo(3);
        assertThat(summary.duplicates()).isZero();
        assertThat(profiles.latest("BANK")).isEqualTo(mapping);
        assertThat(imports.detail(summary.statementId(), "UNMATCHED").lines()).hasSize(3);
        assertThat(imports.detail(summary.statementId(), "MATCHED").lines()).isEmpty();
    }

    @Test
    void overlappingUploadDropsDuplicates() {
        var a = row("2026-02-05", description(), "A", "", "100.00", "1100.00");
        var b = row("2026-02-06", description(), "B", "", "200.00", "1300.00");
        var c = row("2026-02-07", description(), "C", "50.00", "", "1250.00");
        var d = row("2026-02-08", description(), "D", "", "300.00", "1550.00");
        imports.importStatement("s.csv", csv(a, b, c), mapping(), null, null, null);
        var second = imports.importStatement("s.csv", csv(b, c, d), mapping(), null, null, null);
        assertThat(second.added()).isEqualTo(1);
        assertThat(second.duplicates()).isEqualTo(2);
    }

    @Test
    void identicalPairWithDifferentBalancesKept() {
        var narration = description();
        var summary = imports.importStatement(
                "s.csv",
                csv(
                        row("2026-02-10", narration, "PAIR", "", "100.00", "1100.00"),
                        row("2026-02-10", narration, "PAIR", "", "100.00", "1200.00")),
                mapping(),
                null,
                null,
                null);
        assertThat(summary.added()).isEqualTo(2);
        assertThat(summary.duplicates()).isZero();
    }

    @Test
    void openingAndClosingFromBalances() {
        var summary = imports.importStatement(
                "s.csv",
                csv(
                        row("2026-02-12", description(), "OPEN", "", "100.00", "1100.00"),
                        row("2026-02-13", description(), "NEXT", "20.00", "", "1080.00")),
                mapping(),
                null,
                null,
                null);
        var statement = imports.detail(summary.statementId(), null).statement();
        assertThat(statement.openingRupees()).isEqualByComparingTo("1000.00");
        assertThat(statement.closingRupees()).isEqualByComparingTo("1080.00");
        assertThat(statement.lineCount()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownExtension() {
        assertThatThrownBy(() -> imports.importStatement(
                        "s.pdf", "not a spreadsheet".getBytes(UTF_8), mapping(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFileWithNoDatedRows() {
        long before = jdbc.sql("SELECT COUNT(*) FROM bank_statement")
                .query(Long.class)
                .single();
        assertThatThrownBy(() -> imports.importStatement("s.csv", csv(), mapping(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        long after = jdbc.sql("SELECT COUNT(*) FROM bank_statement")
                .query(Long.class)
                .single();
        assertThat(after).isEqualTo(before);
    }
}

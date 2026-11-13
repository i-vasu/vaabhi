package com.vaabhi.accounts;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** COD remittance import: grouping by ref, deduction from the net column, re-uploads and posting on sweep. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class CodRemittanceIT extends BooksFixtures {

    private static final String HEADER = "Remittance ID,Date,UTR,AWB,Order,COD,Deduction,Net";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private CodRemittanceService remittances;

    private static ColumnMapping mapping() {
        return new ColumnMapping(
                0,
                Map.of(
                        "remittanceRef", 0,
                        "remittedOn", 1,
                        "utr", 2,
                        "awb", 3,
                        "orderNumber", 4,
                        "cod", 5,
                        "deduction", 6,
                        "net", 7),
                "yyyy-MM-dd",
                AmountStyle.DEBIT_CREDIT);
    }

    /** The same file with the deduction column left unmapped, so the deduction comes from cod less net. */
    private static ColumnMapping mappingWithoutDeduction() {
        return new ColumnMapping(
                0,
                Map.of(
                        "remittanceRef", 0,
                        "remittedOn", 1,
                        "utr", 2,
                        "awb", 3,
                        "orderNumber", 4,
                        "cod", 5,
                        "net", 7),
                "yyyy-MM-dd",
                AmountStyle.DEBIT_CREDIT);
    }

    private static String today() {
        return LocalDate.now(IST).toString();
    }

    private static String row(
            String ref,
            String date,
            String utr,
            String awb,
            String order,
            String cod,
            String deduction,
            String net) {
        return String.join(",", ref, date, utr, awb, order, cod, deduction, net);
    }

    private static byte[] csv(String... rows) {
        return (HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(UTF_8);
    }

    private CodRemittanceView view(String ref) {
        return remittances.list().stream()
                .filter(candidate -> candidate.remittanceRef().equals(ref))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void groupsRowsByRemittance() {
        var first = unique("RMT");
        var second = unique("RMT");
        var summary = remittances.importReport(
                "remittance.csv",
                csv(
                        row(first, today(), "U1", unique("AWB"), "O1", "1000.00", "50.00", "950.00"),
                        row(first, today(), "", unique("AWB"), "O2", "500.00", "25.00", "475.00"),
                        row(second, today(), "", unique("AWB"), "O3", "300.00", "", "300.00")),
                mapping(),
                "owner");
        assertThat(summary.added()).isEqualTo(2);
        assertThat(summary.duplicates()).isZero();
        assertThat(summary.skipped()).isEmpty();

        var one = view(first);
        assertThat(one.utr()).isEqualTo("U1");
        assertThat(one.codRupees()).isEqualByComparingTo("1500");
        assertThat(one.deductionRupees()).isEqualByComparingTo("75");
        assertThat(one.netRupees()).isEqualByComparingTo("1425");
        assertThat(one.awbCount()).isEqualTo(2);

        var two = view(second);
        assertThat(two.codRupees()).isEqualByComparingTo("300");
        assertThat(two.deductionRupees()).isEqualByComparingTo("0");
        assertThat(two.netRupees()).isEqualByComparingTo("300");
        assertThat(two.awbCount()).isEqualTo(1);
    }

    @Test
    void deductionFromNetWhenNoDeductionColumn() {
        var ref = unique("RMT");
        remittances.importReport(
                "remittance.csv",
                csv(row(ref, today(), "", unique("AWB"), "O1", "2000.00", "", "1900.00")),
                mappingWithoutDeduction(),
                null);

        var imported = view(ref);
        assertThat(imported.deductionRupees()).isEqualByComparingTo("100");
        assertThat(imported.netRupees()).isEqualByComparingTo("1900");
    }

    @Test
    void rerunSkipsKnownRefs() {
        var ref = unique("RMT");
        var bytes = csv(
                row(ref, today(), "", unique("AWB"), "O1", "1000.00", "50.00", "950.00"),
                row(ref, today(), "", unique("AWB"), "O2", "500.00", "25.00", "475.00"));
        var first = remittances.importReport("remittance.csv", bytes, mapping(), null);
        var second = remittances.importReport("remittance.csv", bytes, mapping(), null);

        assertThat(first.added()).isEqualTo(1);
        assertThat(second.added()).isZero();
        assertThat(second.duplicates()).isEqualTo(1);
        assertThat(view(ref).awbCount()).isEqualTo(2);
    }

    @Test
    void unknownAwbStillImportsAndIsListed() {
        var ref = unique("RMT");
        var awb = unique("AWB");
        var summary = remittances.importReport(
                "remittance.csv",
                csv(row(ref, today(), "", awb, "O1", "1000.00", "100.00", "900.00")),
                mapping(),
                null);

        assertThat(summary.added()).isEqualTo(1);
        assertThat(summary.unknownAwbs()).containsExactly(awb);
        var imported = view(ref);
        assertThat(imported.awbCount()).isEqualTo(1);
        long unmatched = jdbc.sql("""
                        SELECT COUNT(*) FROM cod_remittance_line
                        WHERE remittance_id = :id AND web_order_id IS NULL
                        """)
                .param("id", imported.id())
                .query(Long.class)
                .single();
        assertThat(unmatched).isEqualTo(1);
    }

    @Test
    void postsOnSweep() {
        var ref = unique("RMT");
        remittances.importReport(
                "remittance.csv",
                csv(row(ref, today(), "", unique("AWB"), "O1", "2000.00", "100.00", "1900.00")),
                mapping(),
                null);
        long id = jdbc.sql("SELECT id FROM cod_remittance WHERE remittance_ref = :ref")
                .param("ref", ref)
                .query(Long.class)
                .single();

        sweeper.sweepNow();

        var entry = entry("COD_REMITTANCE", id, "SETTLE");
        assertThat(entry.narration()).isEqualTo("Shiprocket COD remittance " + ref);
        assertThat(netInEntry(entry.id(), accountId("BANK"))).isEqualByComparingTo("1900");
        assertThat(netInEntry(entry.id(), accountId("EXPENSE_COURIER"))).isEqualByComparingTo("100");
        assertThat(netInEntry(entry.id(), accountId("COD_CLEARING"))).isEqualByComparingTo("-2000");
    }

    @Test
    void missingRequiredFieldIs400() {
        var mapping = new ColumnMapping(
                0,
                Map.of("remittanceRef", 0, "remittedOn", 1, "cod", 5),
                "yyyy-MM-dd",
                AmountStyle.DEBIT_CREDIT);

        assertThatThrownBy(() -> remittances.importReport(
                        "remittance.csv",
                        csv(row(
                                unique("RMT"), today(), "", unique("AWB"), "O1", "1000.00", "", "1000.00")),
                        mapping,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Map the AWB column");
    }
}

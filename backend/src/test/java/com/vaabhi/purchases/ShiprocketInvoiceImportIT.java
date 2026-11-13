package com.vaabhi.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.accounts.AmountStyle;
import com.vaabhi.accounts.BooksFixtures;
import com.vaabhi.accounts.ColumnMapping;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shiprocket freight invoices: grouping by invoice number, the GST split, repeats and bad rows. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class ShiprocketInvoiceImportIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String HEADER = "Invoice No,Invoice Date,AWB,Freight,GST,Total";

    @Autowired
    private ShiprocketInvoiceImport imports;

    private long supplierId;

    private record BillRow(
            String billNo,
            BigDecimal taxableRupees,
            BigDecimal taxRupees,
            BigDecimal totalRupees,
            String source,
            String status) {}

    private record LineRow(
            BigDecimal cgstRupees, BigDecimal sgstRupees, BigDecimal igstRupees, String description, String hsn) {}

    @BeforeEach
    void useShiprocketSupplier() {
        supplierId = jdbc.sql("""
                        INSERT INTO party (name, is_supplier, state)
                        VALUES (:name, TRUE, 'Maharashtra') RETURNING id
                        """)
                .param("name", unique("Shiprocket "))
                .query(Long.class)
                .single();
        jdbc.sql("UPDATE books_setting SET shiprocket_party_id = :party WHERE id = 1")
                .param("party", supplierId)
                .update();
    }

    @AfterEach
    void clearShiprocketSupplier() {
        jdbc.sql("UPDATE books_setting SET shiprocket_party_id = NULL WHERE id = 1")
                .update();
    }

    private static LocalDate today() {
        return LocalDate.now(IST);
    }

    private static MockMultipartFile csv(String header, String... rows) {
        var text = new StringBuilder(header).append('\n');
        for (var row : rows) {
            text.append(row).append('\n');
        }
        return new MockMultipartFile(
                "file", "freight.csv", "text/csv", text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static ColumnMapping mapping(Map<String, Integer> columns) {
        return new ColumnMapping(0, columns, "yyyy-MM-dd", AmountStyle.SIGNED);
    }

    private static ColumnMapping standardMapping() {
        return mapping(Map.of("invoiceNo", 0, "invoiceDate", 1, "taxable", 3, "gst", 4, "total", 5));
    }

    private List<BillRow> bills() {
        return jdbc.sql("""
                        SELECT bill_no, taxable_rupees, tax_rupees, total_rupees, source, status
                        FROM supplier_bill WHERE party_id = :party ORDER BY bill_no
                        """).param("party", supplierId).query(BillRow.class).list();
    }

    @Test
    void groupsRowsPerInvoice() {
        var file = csv(
                HEADER,
                "SR-1," + today() + ",AWB1,100,18,118",
                "SR-1," + today() + ",AWB2,200,36,236",
                "SR-1," + today() + ",AWB3,300,54,354",
                "SR-2," + today() + ",AWB4,500,90,590");

        var summary = imports.importFile(file, standardMapping(), "owner");

        assertThat(summary.invoicesCreated()).isEqualTo(2);
        assertThat(summary.invoicesSkipped()).isZero();
        assertThat(summary.badRows()).isEmpty();
        assertThat(summary.totalRupees()).isEqualByComparingTo("1298");
        var bills = bills();
        assertThat(bills).hasSize(2);
        assertThat(bills.get(0).billNo()).isEqualTo("SR-1");
        assertThat(bills.get(0).taxableRupees()).isEqualByComparingTo("600");
        assertThat(bills.get(0).taxRupees()).isEqualByComparingTo("108");
        assertThat(bills.get(0).totalRupees()).isEqualByComparingTo("708");
        assertThat(bills.get(0).source()).isEqualTo("SHIPROCKET");
        assertThat(bills.get(0).status()).isEqualTo("OPEN");
        assertThat(bills.get(1).billNo()).isEqualTo("SR-2");
        assertThat(bills.get(1).taxableRupees()).isEqualByComparingTo("500");
        assertThat(bills.get(1).totalRupees()).isEqualByComparingTo("590");
    }

    @Test
    void reimportSkips() {
        var file = csv(HEADER, "SR-1," + today() + ",AWB1,100,18,118", "SR-2," + today() + ",AWB2,500,90,590");
        imports.importFile(file, standardMapping(), null);

        var again = imports.importFile(file, standardMapping(), null);

        assertThat(again.invoicesCreated()).isZero();
        assertThat(again.invoicesSkipped()).isEqualTo(2);
        assertThat(again.skippedInvoiceNos()).containsExactly("SR-1", "SR-2");
        assertThat(bills()).hasSize(2);
    }

    @Test
    void splitColumnsUsedWhenMapped() {
        var file =
                csv("Invoice No,Invoice Date,Freight,CGST,SGST,IGST,Total", "SR-9," + today() + ",1000,90,90,0,1180");
        var mapping = mapping(Map.of(
                "invoiceNo", 0,
                "invoiceDate", 1,
                "taxable", 2,
                "cgst", 3,
                "sgst", 4,
                "igst", 5,
                "total", 6));

        var summary = imports.importFile(file, mapping, null);

        assertThat(summary.invoicesCreated()).isEqualTo(1);
        var bill = bills().getFirst();
        assertThat(bill.taxRupees()).isEqualByComparingTo("180");
        assertThat(bill.totalRupees()).isEqualByComparingTo("1180");
        var line = jdbc.sql("""
                        SELECT l.cgst_rupees, l.sgst_rupees, l.igst_rupees, l.description, l.hsn
                        FROM supplier_bill_line l
                        JOIN supplier_bill b ON b.id = l.bill_id
                        WHERE b.party_id = :party AND b.bill_no = 'SR-9'
                        """).param("party", supplierId).query(LineRow.class).single();
        assertThat(line.cgstRupees()).isEqualByComparingTo("90");
        assertThat(line.sgstRupees()).isEqualByComparingTo("90");
        assertThat(line.igstRupees()).isEqualByComparingTo("0");
        assertThat(line.description()).isEqualTo("Shiprocket freight SR-9");
        assertThat(line.hsn()).isEqualTo("996812");
    }

    @Test
    void noSupplierSetIs409() {
        jdbc.sql("UPDATE books_setting SET shiprocket_party_id = NULL WHERE id = 1")
                .update();
        var file = csv(HEADER, "SR-1," + today() + ",AWB1,100,18,118");

        var error =
                assertThrows(ResponseStatusException.class, () -> imports.importFile(file, standardMapping(), null));

        assertThat(error.getStatusCode().value()).isEqualTo(409);
        assertThat(error.getReason()).contains("Pick the Shiprocket supplier in Books settings first");
        assertThat(bills()).isEmpty();
    }

    @Test
    void badDateRowListed() {
        var file = csv(HEADER, "SR-10," + today() + ",AWB1,100,18,118", "SR-11,not-a-date,AWB2,100,18,118");

        var summary = imports.importFile(file, standardMapping(), null);

        assertThat(summary.invoicesCreated()).isEqualTo(1);
        assertThat(summary.badRows()).hasSize(1);
        assertThat(summary.badRows().getFirst().rowNumber()).isEqualTo(3);
        assertThat(summary.badRows().getFirst().reason()).isEqualTo("no date");
    }
}

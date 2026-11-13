package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.Test;

/** Reading bank and remittance files: CSV and XLSX text, amount styles, and the rows that get skipped. */
class BankFileParserTest {

    @Test
    void readsCsvWithTitleRowsAboveHeader() {
        List<List<String>> rows = readCsv(
                "HDFC BANK",
                "Statement for 01-10-2026 to 31-10-2026",
                "Date,Description,Reference,Debit,Credit,Balance",
                "05/11/2026,UPI   credit  HDFC,UTR123,,\"1,000.00\",\"5,000.00\"",
                "06/11/2026,Bank charges,,50.00,,\"4,950.00\"",
                "Closing balance,,,,,\"4,950.00\"");

        ParsedBankFile file = BankFileParser.parse(rows, bankMapping(2));

        assertThat(file.lines()).hasSize(2);
        ParsedBankLine first = file.lines().getFirst();
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(first.description()).isEqualTo("UPI credit HDFC");
        assertThat(first.reference()).isEqualTo("UTR123");
        assertThat(first.debit()).isEqualByComparingTo("0");
        assertThat(first.credit()).isEqualByComparingTo("1000.00");
        assertThat(first.balance()).isEqualByComparingTo("5000.00");
        ParsedBankLine second = file.lines().get(1);
        assertThat(second.date()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(second.description()).isEqualTo("Bank charges");
        assertThat(second.reference()).isNull();
        assertThat(second.debit()).isEqualByComparingTo("50.00");
        assertThat(second.credit()).isEqualByComparingTo("0");
        assertThat(second.balance()).isEqualByComparingTo("4950.00");
        assertThat(file.skipped()).singleElement().satisfies(row -> {
            assertThat(row.reason()).contains("date");
            assertThat(row.rowNumber()).isEqualTo(6);
        });
    }

    @Test
    void parsesIndianGrouping() {
        assertThat(BankFileParser.parseAmount("1,23,456.78")).isEqualByComparingTo("123456.78");
        assertThat(BankFileParser.parseAmount("₹1,23,456.78")).isEqualByComparingTo("123456.78");
        assertThat(BankFileParser.parseAmount("(1,234.50)")).isEqualByComparingTo("-1234.50");
        assertThat(BankFileParser.parseAmount("")).isEqualByComparingTo("0");
        assertThat(BankFileParser.parseAmount(null)).isEqualByComparingTo("0");
    }

    @Test
    void parsesSignedStyle() {
        List<List<String>> rows = readCsv(
                "Date,Amount,Narration",
                "05/11/2026,-250.00,Charges",
                "06/11/2026,\"1,000.00 Cr\",Salary",
                "07/11/2026,\"-1,000.00 Cr\",Sweep",
                "08/11/2026,750.00 Dr,Cheque");
        ColumnMapping mapping = new ColumnMapping(
                0,
                Map.of("date", 0, "amount", 1, "description", 2),
                "dd/MM/yyyy",
                AmountStyle.SIGNED);

        ParsedBankFile file = BankFileParser.parse(rows, mapping);

        assertThat(file.lines()).hasSize(4);
        assertThat(file.lines().get(0).debit()).isEqualByComparingTo("250.00");
        assertThat(file.lines().get(0).credit()).isEqualByComparingTo("0");
        assertThat(file.lines().get(1).credit()).isEqualByComparingTo("1000.00");
        assertThat(file.lines().get(1).debit()).isEqualByComparingTo("0");
        assertThat(file.lines().get(2).credit()).isEqualByComparingTo("1000.00");
        assertThat(file.lines().get(2).debit()).isEqualByComparingTo("0");
        assertThat(file.lines().get(3).debit()).isEqualByComparingTo("750.00");
        assertThat(file.lines().get(3).credit()).isEqualByComparingTo("0");
    }

    @Test
    void blankAndZeroUnusedColumnBothFine() {
        List<List<String>> rows = readCsv(
                "Date,Debit,Credit",
                "05/11/2026,,500",
                "06/11/2026,0.00,500");
        ColumnMapping mapping = new ColumnMapping(
                0, Map.of("date", 0, "debit", 1, "credit", 2), "dd/MM/yyyy", AmountStyle.DEBIT_CREDIT);

        ParsedBankFile file = BankFileParser.parse(rows, mapping);

        assertThat(file.lines()).hasSize(2);
        assertThat(file.lines()).allSatisfy(line -> {
            assertThat(line.debit()).isEqualByComparingTo("0");
            assertThat(line.credit()).isEqualByComparingTo("500.00");
        });
    }

    @Test
    void rowWithBothSidesSkipped() {
        List<List<String>> rows = readCsv(
                "Date,Debit,Credit",
                "05/11/2026,10.00,10.00",
                "06/11/2026,25.00,");
        ColumnMapping mapping = new ColumnMapping(
                0, Map.of("date", 0, "debit", 1, "credit", 2), "dd/MM/yyyy", AmountStyle.DEBIT_CREDIT);

        ParsedBankFile file = BankFileParser.parse(rows, mapping);

        assertThat(file.lines()).hasSize(1);
        assertThat(file.lines().getFirst().debit()).isEqualByComparingTo("25.00");
        assertThat(file.skipped()).singleElement().satisfies(row -> {
            assertThat(row.reason()).isEqualTo("both debit and credit");
            assertThat(row.rowNumber()).isEqualTo(2);
        });
    }

    @Test
    void zeroAmountRowSkipped() {
        List<List<String>> rows = readCsv(
                "Date,Debit,Credit",
                "05/11/2026,0.00,0.00");
        ColumnMapping mapping = new ColumnMapping(
                0, Map.of("date", 0, "debit", 1, "credit", 2), "dd/MM/yyyy", AmountStyle.DEBIT_CREDIT);

        ParsedBankFile file = BankFileParser.parse(rows, mapping);

        assertThat(file.lines()).isEmpty();
        assertThat(file.skipped()).singleElement().satisfies(row -> assertThat(row.reason()).isEqualTo("no amount"));
    }

    @Test
    void parsesDdMmmYyyy() {
        assertThat(BankFileParser.parseDate("05-Nov-2026", "dd-MMM-yyyy")).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(BankFileParser.parseDate("2026-11-05", "dd-MMM-yyyy")).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(BankFileParser.parseDate("", "dd-MMM-yyyy")).isNull();
        assertThat(BankFileParser.parseDate("not a date", "dd-MMM-yyyy")).isNull();
    }

    @Test
    void readsXlsx() throws IOException {
        List<List<String>> rows = TabularFile.read("statement.xlsx", new ByteArrayInputStream(xlsx()));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("Date", "Description", "Amount");
        assertThat(rows.get(1)).containsExactly("2026-11-05", "UPI CREDIT", "1234.5");
    }

    private static ColumnMapping bankMapping(int headerRow) {
        return new ColumnMapping(
                headerRow,
                Map.of(
                        "date", 0,
                        "description", 1,
                        "reference", 2,
                        "debit", 3,
                        "credit", 4,
                        "balance", 5),
                "dd/MM/yyyy",
                AmountStyle.DEBIT_CREDIT);
    }

    private static List<List<String>> readCsv(String... lines) {
        String text = String.join("\n", lines) + "\n";
        return TabularFile.read("statement.csv", new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] xlsx() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook workbook = new Workbook(out, "vaabhi-test", "1.0")) {
            Worksheet sheet = workbook.newWorksheet("Statement");
            sheet.value(0, 0, "Date");
            sheet.value(0, 1, "Description");
            sheet.value(0, 2, "Amount");
            sheet.value(1, 0, LocalDate.of(2026, 11, 5));
            sheet.style(1, 0).format("dd-mm-yyyy").set();
            sheet.value(1, 1, "UPI CREDIT");
            sheet.value(1, 2, new BigDecimal("1234.5"));
        }
        return out.toByteArray();
    }
}

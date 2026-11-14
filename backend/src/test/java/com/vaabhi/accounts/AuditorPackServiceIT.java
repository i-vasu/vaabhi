package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.rupees.Rupees;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Sheet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The auditor pack: the sheet list and the year's postings read back from the generated workbook. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class AuditorPackServiceIT extends BooksFixtures {

    private static final LocalDate FY_START = LocalDate.of(2091, 4, 1);
    private static final List<String> SHEETS = List.of(
            "About",
            "Trial balance",
            "Profit and loss",
            "Balance sheet",
            "Day book",
            "Ledgers",
            "Debtors",
            "Creditors",
            "Sales register",
            "Purchase register",
            "Fixed assets",
            "Closing stock",
            "Bank reconciliation",
            "GSTR-3B",
            "TDS");

    @Autowired
    private AuditorPackService pack;

    @Autowired
    private JournalService journal;

    private static NewEntry entry(LocalDate date, String narration, long debit, long credit, String amount) {
        return new NewEntry(
                date,
                "MANUAL",
                null,
                "MANUAL",
                narration,
                null,
                null,
                List.of(
                        new NewEntryLine(debit, null, new BigDecimal(amount), Rupees.ZERO, null),
                        new NewEntryLine(credit, null, Rupees.ZERO, new BigDecimal(amount), null)));
    }

    @Test
    void writesEverySheetFromTheYearsEntries() throws IOException {
        var may = LocalDate.of(2091, 5, 10);
        journal.post(entry(may, "Sale", accountId("DEBTORS"), accountId("SALES"), "1000.00"), "test");
        journal.post(entry(may, "Rent", accountId("EXPENSE_RENT"), accountId("CASH"), "300.00"), "test");

        var out = new ByteArrayOutputStream();
        pack.write(FY_START, out);

        try (var workbook =
                new ReadableWorkbook(new ByteArrayInputStream(out.toByteArray()), new ReadingOptions(true, false))) {
            var sheets = workbook.getSheets().toList();
            assertThat(sheets).extracting(Sheet::getName).containsExactlyElementsOf(SHEETS);
            assertThat(textCells(sheets.get(0))).contains("Draft: year not closed");
            assertThat(rowCount(sheets.get(4))).isGreaterThanOrEqualTo(5);
            var total = totalDebitAndCredit(sheets.get(1));
            assertThat(total[0]).isEqualByComparingTo(total[1]);
        }
    }

    private static List<String> textCells(Sheet sheet) throws IOException {
        return sheet.read().stream()
                .flatMap(row -> IntStream.range(0, row.getCellCount())
                        .mapToObj(row::getCell)
                        .filter(Objects::nonNull)
                        .map(AuditorPackServiceIT::text))
                .toList();
    }

    private static long rowCount(Sheet sheet) throws IOException {
        return sheet.read().size();
    }

    private static BigDecimal[] totalDebitAndCredit(Sheet sheet) throws IOException {
        var row = sheet.read().stream()
                .filter(candidate -> "Total".equals(text(candidate.getCell(0))))
                .findFirst()
                .orElseThrow();
        return new BigDecimal[] {
            Objects.requireNonNull(row.getCell(3)).asNumber(),
            Objects.requireNonNull(row.getCell(4)).asNumber()
        };
    }

    private static String text(@Nullable Cell cell) {
        return cell == null ? "" : cell.asString();
    }
}

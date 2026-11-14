package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.junit.jupiter.api.Test;

class TabularFileTest {

    private static InputStream text(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsCommaSeparatedRowsWithQuotes() {
        var rows = TabularFile.read("hdfc.CSV", text("Date,Narration,Amount\n04/11/2026,\"NEFT, LAKSHMI\",10500\n"));
        assertThat(rows)
                .containsExactly(
                        List.of("Date", "Narration", "Amount"), List.of("04/11/2026", "NEFT, LAKSHMI", "10500"));
    }

    @Test
    void dropsTheByteOrderMarkAndPicksSemicolonsOrTabs() {
        assertThat(TabularFile.read("a.csv", text((char) 0xFEFF + "Date;Amount;Note\n01/11/2026;5;x\n")))
                .containsExactly(List.of("Date", "Amount", "Note"), List.of("01/11/2026", "5", "x"));
        assertThat(TabularFile.read("a.csv", text("\n\nDate\tAmount\tNote, more\n01/11/2026\t5\ty\n")))
                .contains(List.of("Date", "Amount", "Note, more"), List.of("01/11/2026", "5", "y"));
    }

    @Test
    void readsAnEmptyFileAsNoRows() {
        assertThat(TabularFile.read("empty.csv", text(""))).isEmpty();
    }

    @Test
    void refusesOtherFiles() {
        assertThatThrownBy(() -> TabularFile.read("old.xls", text("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Save the file as .xlsx or .csv");
        assertThatThrownBy(() -> TabularFile.read("notes.txt", text("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upload a CSV or XLSX file");
        assertThatThrownBy(() -> TabularFile.read(null, text("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Upload a CSV or XLSX file");
    }

    @Test
    void reportsAnUnreadableUpload() {
        var broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk gone");
            }
        };
        assertThatThrownBy(() -> TabularFile.read("a.csv", broken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Could not read the file");
    }

    @Test
    void readsAWorkbookWithTextNumbersDatesAndBooleans() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var workbook = new Workbook(out, "Vaabhi", "1.0")) {
            var sheet = workbook.newWorksheet("Statement");
            sheet.value(0, 0, "Date");
            sheet.value(0, 1, "Amount");
            sheet.value(0, 2, "Cleared");
            sheet.value(1, 0, LocalDate.of(2026, 11, 4));
            sheet.style(1, 0).format("dd-mm-yyyy").set();
            sheet.value(1, 1, 1250);
            sheet.value(1, 2, true);
            sheet.value(1, 4, "after a gap");
        }

        var rows = TabularFile.read("statement.xlsx", new ByteArrayInputStream(out.toByteArray()));

        assertThat(rows.get(0)).containsExactly("Date", "Amount", "Cleared");
        assertThat(rows.get(1)).containsExactly("2026-11-04", "1250", "true", "", "after a gap");
    }
}

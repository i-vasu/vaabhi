package com.vaabhi.accounts;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.jspecify.annotations.Nullable;

/** Reads a CSV or XLSX upload into text rows, one list per file row. */
public final class TabularFile {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final char BOM = (char) 0xFEFF;

    private TabularFile() {}

    /**
     * The file's rows as text, including title and footer rows; the caller's column mapping decides which matter.
     * Dates in an XLSX become {@code yyyy-MM-dd} and numbers lose their exponent.
     */
    public static List<List<String>> read(String fileName, InputStream in) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) {
                return readCsv(in);
            }
            if (name.endsWith(".xlsx")) {
                return readXlsx(in);
            }
            if (name.endsWith(".xls")) {
                throw new IllegalArgumentException("Save the file as .xlsx or .csv");
            }
            throw new IllegalArgumentException("Upload a CSV or XLSX file");
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the file", e);
        }
    }

    private static List<List<String>> readCsv(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter(text)).build();
        List<List<String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            for (CSVRecord record : parser) {
                rows.add(record.toList());
            }
        }
        return rows;
    }

    /** The delimiter with the most occurrences on the first non-empty line; comma when there is none. */
    private static char delimiter(String text) {
        for (String line : text.split("\\R", -1)) {
            if (line.isBlank()) {
                continue;
            }
            int commas = count(line, ',');
            int semicolons = count(line, ';');
            int tabs = count(line, '\t');
            if (tabs > commas && tabs > semicolons) {
                return '\t';
            }
            if (semicolons > commas) {
                return ';';
            }
            return ',';
        }
        return ',';
    }

    private static int count(String line, char character) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == character) {
                count++;
            }
        }
        return count;
    }

    private static List<List<String>> readXlsx(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (ReadableWorkbook workbook = new ReadableWorkbook(in, new ReadingOptions(true, false))) {
            Sheet sheet = workbook.getFirstSheet();
            for (Row row : sheet.read()) {
                List<String> cells = new ArrayList<>(row.getCellCount());
                for (int column = 0; column < row.getCellCount(); column++) {
                    cells.add(text(row.getCell(column)));
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private static String text(@Nullable Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getType();
        if (type == CellType.STRING) {
            return cell.asString();
        }
        if (type == CellType.NUMBER) {
            return isDateFormatted(cell) ? dateText(cell) : numberText(cell);
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.asBoolean());
        }
        return "";
    }

    private static String dateText(Cell cell) {
        LocalDateTime date = cell.asDate();
        return date == null ? "" : date.toLocalDate().format(ISO_DATE);
    }

    private static String numberText(Cell cell) {
        BigDecimal number = cell.asNumber();
        return number == null ? "" : number.toPlainString();
    }

    /** XLSX stores a date as a number; its data format is what tells it apart from a plain number. */
    private static boolean isDateFormatted(Cell cell) {
        Integer formatId = cell.getDataFormatId();
        if (formatId != null && ((formatId >= 14 && formatId <= 22) || (formatId >= 45 && formatId <= 47))) {
            return true;
        }
        String format = cell.getDataFormatString();
        if (format == null) {
            return false;
        }
        String lower = format.toLowerCase(Locale.ROOT);
        return lower.contains("yy") || lower.contains("dd") || lower.contains("mmm");
    }
}

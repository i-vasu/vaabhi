package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Turns the rows of a bank or remittance file into lines using a column mapping. Rows without a date (titles,
 * footers, blank rows) and rows that are not a single money movement are skipped, with the reason.
 */
public final class BankFileParser {

    private static final int DESCRIPTION_MAX = 300;
    private static final int REFERENCE_MAX = 100;

    private BankFileParser() {}

    public static ParsedBankFile parse(List<List<String>> rows, ColumnMapping mapping) {
        List<ParsedBankLine> lines = new ArrayList<>();
        List<SkippedFileRow> skipped = new ArrayList<>();
        for (int index = mapping.headerRow() + 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            int rowNumber = index + 1;
            LocalDate date = parseDate(cell(row, mapping, "date"), mapping.dateFormat());
            if (date == null) {
                skipped.add(new SkippedFileRow(rowNumber, "no date"));
                continue;
            }

            BigDecimal debit;
            BigDecimal credit;
            if (mapping.amountStyle() == AmountStyle.SIGNED) {
                String raw = cell(row, mapping, "amount");
                BigDecimal amount = parseAmount(raw);
                String suffix = amountSuffix(raw);
                boolean debitSuffix = suffix.equals("dr");
                boolean creditSuffix = suffix.equals("cr");
                debit = debitSuffix || (suffix.isEmpty() && amount.signum() < 0) ? amount.abs() : Rupees.ZERO;
                credit = creditSuffix || (suffix.isEmpty() && amount.signum() > 0) ? amount.abs() : Rupees.ZERO;
            } else {
                debit = parseAmount(cell(row, mapping, "debit")).abs();
                credit = parseAmount(cell(row, mapping, "credit")).abs();
            }

            if (debit.signum() > 0 && credit.signum() > 0) {
                skipped.add(new SkippedFileRow(rowNumber, "both debit and credit"));
                continue;
            }
            if (debit.signum() == 0 && credit.signum() == 0) {
                skipped.add(new SkippedFileRow(rowNumber, "no amount"));
                continue;
            }

            String balanceCell = cell(row, mapping, "balance");
            BigDecimal balance = balanceCell.isBlank() ? null : parseAmount(balanceCell);
            lines.add(new ParsedBankLine(
                    rowNumber,
                    date,
                    description(cell(row, mapping, "description")),
                    reference(cell(row, mapping, "reference")),
                    debit,
                    credit,
                    balance));
        }
        return new ParsedBankFile(lines, skipped);
    }

    /**
     * The amount in the cell as rupees: strips {@code ₹}, grouping commas, spaces and a {@code Cr}/{@code Dr}
     * suffix; parentheses mean negative. Blank or unreadable cells are zero.
     */
    public static BigDecimal parseAmount(@Nullable String raw) {
        if (raw == null) {
            return Rupees.ZERO;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return Rupees.ZERO;
        }
        text = text.replace("₹", "").replace(",", "").replaceAll("\\s+", "");
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.endsWith("CR.") || upper.endsWith("DR.")) {
            text = text.substring(0, text.length() - 3);
        } else if (upper.endsWith("CR") || upper.endsWith("DR")) {
            text = text.substring(0, text.length() - 2);
        }
        boolean negative = false;
        if (text.startsWith("(") && text.endsWith(")") && text.length() > 1) {
            negative = true;
            text = text.substring(1, text.length() - 1);
        }
        if (text.startsWith("-")) {
            negative = !negative;
            text = text.substring(1);
        } else if (text.startsWith("+")) {
            text = text.substring(1);
        }
        if (text.isEmpty()) {
            return Rupees.ZERO;
        }
        try {
            BigDecimal amount = new BigDecimal(text);
            return Rupees.of(negative ? amount.negate() : amount);
        } catch (NumberFormatException e) {
            return Rupees.ZERO;
        }
    }

    /** The date in the cell, trying the mapping's pattern first and {@code yyyy-MM-dd} second. */
    public static @Nullable LocalDate parseDate(@Nullable String raw, @Nullable String pattern) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (pattern != null && !pattern.isBlank()) {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
                // Try the ISO format next.
            }
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String cell(List<String> row, ColumnMapping mapping, String key) {
        Map<String, Integer> columns = mapping.columns();
        Integer index = columns == null ? null : columns.get(key);
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value;
    }

    /** {@code dr} or {@code cr} when the amount's text ends with that suffix, otherwise empty. */
    private static String amountSuffix(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (text.endsWith("CR.") || text.endsWith("DR.")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith("CR")) {
            return "cr";
        }
        if (text.endsWith("DR")) {
            return "dr";
        }
        return "";
    }

    private static String description(String raw) {
        String text = raw.trim().replaceAll("\\s+", " ");
        return text.length() > DESCRIPTION_MAX ? text.substring(0, DESCRIPTION_MAX) : text;
    }

    private static @Nullable String reference(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > REFERENCE_MAX ? text.substring(0, REFERENCE_MAX) : text;
    }
}

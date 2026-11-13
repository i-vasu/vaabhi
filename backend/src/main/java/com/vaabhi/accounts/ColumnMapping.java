package com.vaabhi.accounts;

import java.util.Map;

/**
 * Where the fields of an uploaded bank or remittance file sit.
 *
 * @param headerRow the 0-based index of the header row; rows above it are titles and below it are data
 * @param columns the 0-based column index for each field name
 * @param dateFormat the pattern the date column uses, e.g. {@code dd/MM/yyyy}; {@code yyyy-MM-dd} is also tried
 * @param amountStyle whether the file has separate debit and credit columns or one signed amount column
 */
public record ColumnMapping(
        int headerRow, Map<String, Integer> columns, String dateFormat, AmountStyle amountStyle) {}

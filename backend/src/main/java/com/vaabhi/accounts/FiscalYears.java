package com.vaabhi.accounts;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Financial years run April to March and are named "2026-27". */
public final class FiscalYears {

    private static final int TWELVE_MONTHS = 12;

    private FiscalYears() {}

    /** The 1 April of the financial year containing {@code date}. */
    public static LocalDate startOf(LocalDate date) {
        int year = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return LocalDate.of(year, 4, 1);
    }

    /** The 31 March that closes the year starting on {@code fyStart}. */
    public static LocalDate end(LocalDate fyStart) {
        return fyStart.plusYears(1).minusDays(1);
    }

    public static String label(LocalDate fyStart) {
        return fyStart.getYear() + "-%02d".formatted((fyStart.getYear() + 1) % 100);
    }

    public static LocalDate parse(String label) {
        if (label == null || !label.matches("\\d{4}-\\d{2}")) {
            throw badLabel();
        }
        int year = Integer.parseInt(label.substring(0, 4));
        int second = Integer.parseInt(label.substring(5));
        if (second != (year + 1) % 100) {
            throw badLabel();
        }
        return LocalDate.of(year, 4, 1);
    }

    /** The twelve months of the year, April first. */
    public static List<YearMonth> months(LocalDate fyStart) {
        var months = new ArrayList<YearMonth>(TWELVE_MONTHS);
        for (int i = 0; i < TWELVE_MONTHS; i++) {
            months.add(YearMonth.from(fyStart).plusMonths(i));
        }
        return months;
    }

    private static ResponseStatusException badLabel() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Financial year must look like 2026-27");
    }
}

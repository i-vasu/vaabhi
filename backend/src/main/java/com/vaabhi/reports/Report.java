package com.vaabhi.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A tabular report.
 *
 * @param rows one map per row, keyed by column key; MONEY values are paise
 * @param totals column key → total, for the columns that have one
 * @param note anything the reader should know about how it is counted
 */
public record Report(
        String name,
        String title,
        LocalDate from,
        LocalDate to,
        List<Column> columns,
        List<Map<String, Object>> rows,
        Map<String, Object> totals,
        @Nullable String note) {

    /** How a column's values are shown. */
    public enum Type {
        TEXT,
        NUMBER,
        MONEY,
        DATE,
        PERCENT
    }

    public record Column(String key, String label, Type type) {}

    /** One report the dashboard can open. */
    public record ReportInfo(String name, String title, String description) {}

    /** Booked, collected and spent in one month. */
    public record MonthFigures(String month, long bookedPaise, long collectedPaise, long expensesPaise) {}

    /** Money owed, by how late it is. */
    public record AgeingBucket(String label, int count, long paise) {}

    /**
     * The reports hub.
     *
     * @param averageOrderPaise booked ÷ orders booked this month
     */
    public record ReportsOverview(
            long bookedPaise,
            long collectedPaise,
            long expensesPaise,
            int ordersBooked,
            int ordersDelivered,
            long averageOrderPaise,
            int newCustomers,
            long outstandingPaise,
            List<MonthFigures> months,
            List<AgeingBucket> ageing) {}
}

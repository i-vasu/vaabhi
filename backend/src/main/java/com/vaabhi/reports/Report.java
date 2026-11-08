package com.vaabhi.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A tabular report.
 *
 * @param rows one map per row, keyed by column key; MONEY values are rupees
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
    public record MonthFigures(
            String month, BigDecimal bookedRupees, BigDecimal collectedRupees, BigDecimal expensesRupees) {}

    /** Money owed, by how late it is. */
    public record AgeingBucket(String label, int count, BigDecimal rupees) {}

    /**
     * The reports hub.
     *
     * @param averageOrderRupees booked ÷ orders booked this month
     */
    public record ReportsOverview(
            BigDecimal bookedRupees,
            BigDecimal collectedRupees,
            BigDecimal expensesRupees,
            int ordersBooked,
            int ordersDelivered,
            BigDecimal averageOrderRupees,
            int newCustomers,
            BigDecimal outstandingRupees,
            List<MonthFigures> months,
            List<AgeingBucket> ageing) {}
}

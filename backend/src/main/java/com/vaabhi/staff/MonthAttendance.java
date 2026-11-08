package com.vaabhi.staff;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A month of attendance: one row per person with a status (or null) for each day. */
public record MonthAttendance(LocalDate from, LocalDate to, List<Row> rows) {

    /**
     * @param days status for each day of the month, first day first; null where not marked
     * @param offDays OFF, HOLIDAY and COMP_OFF days
     */
    public record Row(
            long staffId,
            String name,
            List<@Nullable AttendanceStatus> days,
            int present,
            int halfDays,
            int absent,
            int leave,
            int offDays,
            int unmarked,
            int lateMinutes,
            int overtimeMinutes) {}
}

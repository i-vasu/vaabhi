package com.vaabhi.staff;

import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * One person on one day.
 *
 * @param status null while not marked
 * @param suggested what an unmarked day probably is: OFF on their weekly off, HOLIDAY when the shop is closed
 */
public record AttendanceEntry(
        long staffId,
        String name,
        @Nullable String designation,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        @Nullable AttendanceStatus status,
        @Nullable AttendanceStatus suggested,
        @Nullable LocalTime checkIn,
        @Nullable LocalTime checkOut,
        int lateMinutes,
        int overtimeMinutes,
        @Nullable String note,
        @Nullable String markedBy) {}

package com.vaabhi.staff;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Everyone active on one day.
 *
 * @param holiday the shop holiday's name, or "Weekly off", when the shop is closed that day
 */
public record DayAttendance(LocalDate day, @Nullable String holiday, List<AttendanceEntry> entries) {}

package com.vaabhi.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Days the shop is closed: attendance, payroll and due dates skip them. */
public record CalendarSettings(
        @NotNull Set<DayOfWeek> weeklyOff,
        @NotNull @Size(max = 200) List<@Valid Holiday> holidays) {

    public record Holiday(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 80) String name) {}

    public boolean isClosed(LocalDate date) {
        return weeklyOff.contains(date.getDayOfWeek())
                || holidays.stream().anyMatch(holiday -> holiday.date().equals(date));
    }

    static CalendarSettings defaults() {
        return new CalendarSettings(Set.of(), List.of());
    }
}

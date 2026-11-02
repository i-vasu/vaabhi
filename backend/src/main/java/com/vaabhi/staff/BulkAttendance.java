package com.vaabhi.staff;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Mark several people with the same status (e.g. everyone present), without times. */
public record BulkAttendance(
        @NotNull AttendanceStatus status,
        @NotNull @Size(min = 1, max = 200) List<@NotNull Long> staffIds) {}

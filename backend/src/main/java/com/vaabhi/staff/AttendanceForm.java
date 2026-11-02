package com.vaabhi.staff;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * Mark one person's day. Check-in and check-out count only on days worked; late and overtime minutes are worked
 * out from their shift.
 */
public record AttendanceForm(
        @NotNull AttendanceStatus status,
        @Nullable LocalTime checkIn,
        @Nullable LocalTime checkOut,
        @Nullable @Size(max = 200) String note) {}

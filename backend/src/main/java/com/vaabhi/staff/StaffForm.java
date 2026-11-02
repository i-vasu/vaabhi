package com.vaabhi.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * Add or edit a staff member.
 *
 * @param email needed only to sign in to the dashboard
 * @param password sets (or, when editing, replaces) their dashboard password; null keeps it as it is
 */
public record StaffForm(
        @NotBlank @Size(max = 120) String name,
        @Nullable @Size(max = 20) String phone,
        @Nullable @Email @Size(max = 254) String email,
        @NotNull StaffRole role,
        @Nullable @Size(max = 60) String designation,
        @Nullable LocalDate joinedOn,
        @NotNull PayType payType,
        @Min(0) @Max(100_000_000) long salaryPaise,
        @Min(0) @Max(10_000_000) long hourlyPaise,
        @NotNull LocalTime shiftStart,
        @NotNull LocalTime shiftEnd,
        @Min(0) @Max(180) int lateGraceMinutes,
        @Nullable @Min(1) @Max(7) Integer weeklyOff,

        @Nullable @Size(min = 10, max = 200, message = "must be at least 10 characters")
        String password) {}

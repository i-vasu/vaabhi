package com.vaabhi.staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Someone who works at the shop, with their pay and shift.
 *
 * @param salaryRupees a month (MONTHLY) or a day worked (DAILY); unused for PIECE
 * @param hourlyRupees paid per hour of overtime, and what an hour late costs
 * @param weeklyOff ISO day of the week off (1 = Monday … 7 = Sunday)
 * @param canSignIn has a password and may sign in to the dashboard
 */
public record StaffMember(
        long id,
        String name,
        @Nullable String phone,
        @Nullable String email,
        StaffRole role,
        @Nullable String designation,
        @Nullable LocalDate joinedOn,
        PayType payType,
        BigDecimal salaryRupees,
        BigDecimal hourlyRupees,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        int lateGraceMinutes,
        @Nullable Integer weeklyOff,
        boolean active,
        boolean canSignIn,
        @Nullable OffsetDateTime lastLoginAt) {}

package com.vaabhi.payroll;

import com.vaabhi.staff.PayType;
import com.vaabhi.tailoring.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One person's pay for a month.
 *
 * @param id null while the month is only a preview (not finalised)
 * @param month first day of the month
 * @param ratePaise monthly salary, daily wage, or 0 for piece work
 * @param daysPaid days the salary or wage is paid for
 * @param daysUnpaid absent and leave days (half days count half)
 * @param piecePaise piece pay (piece workers only); {@code pieces} is shown for everyone
 * @param advancePaise approved advances taken back this month
 * @param netPaise base + piece + overtime − late − advance (negative when advances exceed earnings)
 */
public record Payslip(
        @Nullable Long id,
        long staffId,
        String name,
        @Nullable String designation,
        LocalDate month,
        PayType payType,
        long ratePaise,
        BigDecimal daysPaid,
        BigDecimal daysUnpaid,
        long basePaise,
        long piecePaise,
        int pieces,
        int overtimeMinutes,
        long overtimePaise,
        int lateMinutes,
        long latePaise,
        long advancePaise,
        long netPaise,
        @Nullable OffsetDateTime paidAt,
        @Nullable PaymentMode paidMode) {}

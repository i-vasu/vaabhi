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
 * @param rateRupees monthly salary, daily wage, or 0 for piece work
 * @param daysPaid days the salary or wage is paid for
 * @param daysUnpaid absent and leave days (half days count half)
 * @param pieceRupees piece pay (piece workers only); {@code pieces} is shown for everyone
 * @param advanceRupees approved advances taken back this month
 * @param netRupees base + piece + overtime − late − advance (negative when advances exceed earnings)
 */
public record Payslip(
        @Nullable Long id,
        long staffId,
        String name,
        @Nullable String designation,
        LocalDate month,
        PayType payType,
        BigDecimal rateRupees,
        BigDecimal daysPaid,
        BigDecimal daysUnpaid,
        BigDecimal baseRupees,
        BigDecimal pieceRupees,
        int pieces,
        int overtimeMinutes,
        BigDecimal overtimeRupees,
        int lateMinutes,
        BigDecimal lateRupees,
        BigDecimal advanceRupees,
        BigDecimal netRupees,
        @Nullable OffsetDateTime paidAt,
        @Nullable PaymentMode paidMode) {}

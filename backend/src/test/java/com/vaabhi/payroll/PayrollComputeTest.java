package com.vaabhi.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.staff.MonthAttendance;
import com.vaabhi.staff.PayType;
import com.vaabhi.staff.StaffMember;
import com.vaabhi.staff.StaffRole;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The payslip arithmetic. */
class PayrollComputeTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11); // 30 days

    private static StaffMember member(PayType payType, long salaryPaise, long hourlyPaise) {
        return new StaffMember(
                7,
                "Ravi",
                null,
                null,
                StaffRole.STAFF,
                "Tailor",
                null,
                payType,
                salaryPaise,
                hourlyPaise,
                LocalTime.of(10, 0),
                LocalTime.of(19, 0),
                10,
                null,
                true,
                false,
                null);
    }

    private static MonthAttendance.Row days(int present, int half, int absent, int leave, int late, int overtime) {
        return new MonthAttendance.Row(7, "Ravi", List.of(), present, half, absent, leave, 0, 0, late, overtime);
    }

    @Test
    void monthlySalaryLosesUnpaidDaysOnly() {
        // ₹15,000 a month; 2 absent, 1 leave, 1 half day → 3.5 unpaid of 30 → 26.5 / 30 × 15,000 = 13,250.
        var slip = PayrollService.compute(
                member(PayType.MONTHLY, 1_500_000, 0), NOVEMBER, days(20, 1, 2, 1, 0, 0), 0, 0, 0);
        assertThat(slip.daysUnpaid()).isEqualByComparingTo(new BigDecimal("3.5"));
        assertThat(slip.daysPaid()).isEqualByComparingTo(new BigDecimal("26.5"));
        assertThat(slip.basePaise()).isEqualTo(1_325_000);
        assertThat(slip.netPaise()).isEqualTo(1_325_000);
    }

    @Test
    void unmarkedMonthPaysFullSalary() {
        var slip = PayrollService.compute(member(PayType.MONTHLY, 1_500_000, 0), NOVEMBER, null, 0, 0, 0);
        assertThat(slip.basePaise()).isEqualTo(1_500_000);
    }

    @Test
    void dailyWageIsForDaysWorked() {
        // ₹600 a day × (22 + 0.5) days.
        var slip = PayrollService.compute(member(PayType.DAILY, 60_000, 0), NOVEMBER, days(22, 1, 3, 0, 0, 0), 0, 0, 0);
        assertThat(slip.daysPaid()).isEqualByComparingTo(new BigDecimal("22.5"));
        assertThat(slip.basePaise()).isEqualTo(1_350_000);
    }

    @Test
    void pieceWorkersArePaidTheirPiecesAndOthersOnlySeeTheCount() {
        var piece =
                PayrollService.compute(member(PayType.PIECE, 0, 0), NOVEMBER, days(20, 0, 0, 0, 0, 0), 14, 420_000, 0);
        assertThat(piece.basePaise()).isZero();
        assertThat(piece.piecePaise()).isEqualTo(420_000);
        assertThat(piece.netPaise()).isEqualTo(420_000);

        var monthly = PayrollService.compute(member(PayType.MONTHLY, 1_000_000, 0), NOVEMBER, null, 14, 420_000, 0);
        assertThat(monthly.pieces()).isEqualTo(14);
        assertThat(monthly.piecePaise()).isZero();
    }

    @Test
    void overtimeLatenessAndAdvancesAdjustTheNet() {
        // ₹120 an hour: 150 min overtime = ₹300; 45 min late = ₹90; ₹2,000 advance back.
        var slip = PayrollService.compute(
                member(PayType.MONTHLY, 1_500_000, 12_000), NOVEMBER, days(26, 0, 0, 0, 45, 150), 0, 0, 200_000);
        assertThat(slip.overtimePaise()).isEqualTo(30_000);
        assertThat(slip.latePaise()).isEqualTo(9_000);
        assertThat(slip.netPaise()).isEqualTo(1_500_000 + 30_000 - 9_000 - 200_000);
    }

    @Test
    void anAdvanceLargerThanThePayLeavesANegativeNet() {
        var slip = PayrollService.compute(member(PayType.PIECE, 0, 0), NOVEMBER, null, 1, 5_000, 20_000);
        assertThat(slip.netPaise()).isEqualTo(-15_000);
    }
}

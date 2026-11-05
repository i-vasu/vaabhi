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

    private static StaffMember member(PayType payType, BigDecimal salaryRupees, BigDecimal hourlyRupees) {
        return new StaffMember(
                7,
                "Ravi",
                null,
                null,
                StaffRole.STAFF,
                "Tailor",
                null,
                payType,
                salaryRupees,
                hourlyRupees,
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

    private static BigDecimal r(String rupees) {
        return new BigDecimal(rupees);
    }

    @Test
    void monthlySalaryLosesUnpaidDaysOnly() {
        // ₹15,000 a month; 2 absent, 1 leave, 1 half day → 3.5 unpaid of 30 → 26.5 / 30 × 15,000 = 13,250.
        var slip = PayrollService.compute(
                member(PayType.MONTHLY, r("15000"), r("0")), NOVEMBER, days(20, 1, 2, 1, 0, 0), 0, r("0"), r("0"));
        assertThat(slip.daysUnpaid()).isEqualByComparingTo(new BigDecimal("3.5"));
        assertThat(slip.daysPaid()).isEqualByComparingTo(new BigDecimal("26.5"));
        assertThat(slip.baseRupees()).isEqualByComparingTo(r("13250"));
        assertThat(slip.netRupees()).isEqualByComparingTo(r("13250"));
    }

    @Test
    void unmarkedMonthPaysFullSalary() {
        var slip =
                PayrollService.compute(member(PayType.MONTHLY, r("15000"), r("0")), NOVEMBER, null, 0, r("0"), r("0"));
        assertThat(slip.baseRupees()).isEqualByComparingTo(r("15000"));
    }

    @Test
    void dailyWageIsForDaysWorked() {
        // ₹600 a day × (22 + 0.5) days.
        var slip = PayrollService.compute(
                member(PayType.DAILY, r("600"), r("0")), NOVEMBER, days(22, 1, 3, 0, 0, 0), 0, r("0"), r("0"));
        assertThat(slip.daysPaid()).isEqualByComparingTo(new BigDecimal("22.5"));
        assertThat(slip.baseRupees()).isEqualByComparingTo(r("13500"));
    }

    @Test
    void pieceWorkersArePaidTheirPiecesAndOthersOnlySeeTheCount() {
        var piece = PayrollService.compute(
                member(PayType.PIECE, r("0"), r("0")), NOVEMBER, days(20, 0, 0, 0, 0, 0), 14, r("4200"), r("0"));
        assertThat(piece.baseRupees()).isZero();
        assertThat(piece.pieceRupees()).isEqualByComparingTo(r("4200"));
        assertThat(piece.netRupees()).isEqualByComparingTo(r("4200"));

        var monthly = PayrollService.compute(
                member(PayType.MONTHLY, r("10000"), r("0")), NOVEMBER, null, 14, r("4200"), r("0"));
        assertThat(monthly.pieces()).isEqualTo(14);
        assertThat(monthly.pieceRupees()).isZero();
    }

    @Test
    void overtimeLatenessAndAdvancesAdjustTheNet() {
        // ₹120 an hour: 150 min overtime = ₹300; 45 min late = ₹90; ₹2,000 advance back.
        var slip = PayrollService.compute(
                member(PayType.MONTHLY, r("15000"), r("120")),
                NOVEMBER,
                days(26, 0, 0, 0, 45, 150),
                0,
                r("0"),
                r("2000"));
        assertThat(slip.overtimeRupees()).isEqualByComparingTo(r("300"));
        assertThat(slip.lateRupees()).isEqualByComparingTo(r("90"));
        assertThat(slip.netRupees()).isEqualByComparingTo(r("13210"));
    }

    @Test
    void anAdvanceLargerThanThePayLeavesANegativeNet() {
        var slip = PayrollService.compute(member(PayType.PIECE, r("0"), r("0")), NOVEMBER, null, 1, r("50"), r("200"));
        assertThat(slip.netRupees()).isEqualByComparingTo(r("-150"));
    }
}

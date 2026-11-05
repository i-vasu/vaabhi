package com.vaabhi.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Late and overtime minutes from a shift. */
class AttendanceMathTest {

    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Test
    void lateCountsInFullOnceBeyondTheGracePeriod() {
        assertThat(AttendanceService.lateMinutes(LocalTime.of(10, 10), TEN, 10)).isZero();
        assertThat(AttendanceService.lateMinutes(LocalTime.of(10, 11), TEN, 10)).isEqualTo(11);
        assertThat(AttendanceService.lateMinutes(LocalTime.of(9, 45), TEN, 10)).isZero();
        assertThat(AttendanceService.lateMinutes(null, TEN, 10)).isZero();
    }

    @Test
    void overtimeIsTimeAfterTheShiftEnds() {
        var seven = LocalTime.of(19, 0);
        assertThat(AttendanceService.overtimeMinutes(LocalTime.of(20, 30), seven))
                .isEqualTo(90);
        assertThat(AttendanceService.overtimeMinutes(LocalTime.of(18, 30), seven))
                .isZero();
        assertThat(AttendanceService.overtimeMinutes(null, seven)).isZero();
    }
}

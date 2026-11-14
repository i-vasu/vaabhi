package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScheduleLineTest {

    @Test
    void defaultsByType() {
        assertThat(ScheduleLine.defaultFor(AccountType.INCOME)).isEqualTo(ScheduleLine.OTHER_INCOME);
        assertThat(ScheduleLine.defaultFor(AccountType.ASSET)).isEqualTo(ScheduleLine.OTHER_CURRENT_ASSETS);
    }

    @Test
    void fitsOnlyItsOwnSide() {
        assertThat(ScheduleLine.PPE.fits(AccountType.ASSET)).isTrue();
        assertThat(ScheduleLine.REVENUE_FROM_OPERATIONS.fits(AccountType.EXPENSE))
                .isFalse();
    }
}

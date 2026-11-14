package com.vaabhi.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class BackupKeysTest {

    @Test
    void dailyKeyIsNamedInIst() {
        ZonedDateTime oneAmIst = ZonedDateTime.of(2026, 11, 14, 1, 0, 0, 0, ZoneId.of("Asia/Kolkata"));

        assertThat(BackupKeys.daily(oneAmIst)).isEqualTo("db/2026/11/vaabhi-2026-11-14T0100.dump");
    }

    @Test
    void monthEndIsTheLastDayOfTheMonth() {
        assertThat(BackupKeys.isMonthEnd(LocalDate.of(2027, 1, 31))).isTrue();
        assertThat(BackupKeys.isMonthEnd(LocalDate.of(2027, 2, 1))).isFalse();
        assertThat(BackupKeys.isMonthEnd(LocalDate.of(2028, 2, 29))).isTrue();
    }

    @Test
    void monthlyKeyIsNamedByMonth() {
        assertThat(BackupKeys.monthly(LocalDate.of(2027, 1, 31))).isEqualTo("monthly/vaabhi-2027-01.dump");
    }

    @Test
    void attachmentsKeepTheirSourceKey() {
        assertThat(BackupKeys.attachment("bills/7/ab.pdf")).isEqualTo("attachments/bills/7/ab.pdf");
    }

    @Test
    void retainsDailyForThirtyFiveDays() {
        Instant now = Instant.parse("2026-11-14T00:00:00Z");

        assertThat(BackupKeys.dailyRetainUntil(now)).isEqualTo(now.plus(35, ChronoUnit.DAYS));
    }

    @Test
    void retainsTheLongCopyForEightYearsAndADay() {
        Instant now = Instant.parse("2026-11-14T00:00:00Z");

        assertThat(BackupKeys.longRetainUntil(now))
                .isEqualTo(now.atZone(ZoneOffset.UTC).plusYears(8).plusDays(1).toInstant());
    }
}

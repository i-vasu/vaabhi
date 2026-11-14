package com.vaabhi.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Missed-night detection around the 02:00 IST cut-over. */
class BackupStatusTest {

    @Test
    void anEarlyMorningRunCountsForTheNight() {
        var now = at("2026-11-14T01:30");
        assertThat(BackupStatusService.missedLastNight(now, at("2026-11-14T00:30")))
                .isFalse();
    }

    @Test
    void theNextMorningReportsAMissingRun() {
        var now = at("2026-11-15T02:30");
        assertThat(BackupStatusService.missedLastNight(now, at("2026-11-14T00:30")))
                .isTrue();
    }

    @Test
    void beforeTwoAmYesterdaysLateRunStillCounts() {
        var now = at("2026-11-14T01:59");
        assertThat(BackupStatusService.missedLastNight(now, at("2026-11-13T01:05")))
                .isFalse();
    }

    @Test
    void fromTwoAmTheRunMustBeTodays() {
        var now = at("2026-11-14T02:00");
        assertThat(BackupStatusService.missedLastNight(now, at("2026-11-13T23:00")))
                .isTrue();
    }

    @Test
    void noRunAtAllIsMissed() {
        assertThat(BackupStatusService.missedLastNight(at("2026-11-14T03:00"), null))
                .isTrue();
    }

    private static Instant at(String istLocalTime) {
        return LocalDateTime.parse(istLocalTime).atZone(BackupKeys.IST).toInstant();
    }
}

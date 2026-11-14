package com.vaabhi.backup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Object keys and Object Lock retain-until dates for the backups. Keys are named in IST, like the shop and the lock
 * dates. The daily dump lives 35 days; a month-end copy and every attachment live 8 years plus a day.
 */
final class BackupKeys {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter DAILY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private BackupKeys() {}

    static String daily(ZonedDateTime ist) {
        return "db/" + ist.getYear() + "/" + "%02d".formatted(ist.getMonthValue()) + "/vaabhi-"
                + ist.format(DAILY_STAMP) + ".dump";
    }

    static boolean isMonthEnd(LocalDate istDate) {
        return istDate.equals(YearMonth.from(istDate).atEndOfMonth());
    }

    static String monthly(LocalDate istDate) {
        return "monthly/vaabhi-" + YearMonth.from(istDate).format(MONTH) + ".dump";
    }

    static String attachment(String sourceKey) {
        return "attachments/" + sourceKey;
    }

    static Instant dailyRetainUntil(Instant now) {
        return now.plus(35, ChronoUnit.DAYS);
    }

    static Instant longRetainUntil(Instant now) {
        return now.atZone(UTC).plusYears(8).plusDays(1).toInstant();
    }
}

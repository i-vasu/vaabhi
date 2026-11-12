package com.vaabhi.accounts;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Month locks: a locked month takes no new entries. */
@Service
public class PeriodService {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private final JdbcClient jdbc;
    private final BooksSettingsService settings;
    private final Clock clock;

    PeriodService(JdbcClient jdbc, BooksSettingsService settings, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
    }

    record LockRow(LocalDate month, @Nullable String lockedBy, OffsetDateTime lockedAt) {}

    /** Months from the books start date to this month, newest first; empty until the books have a start date. */
    @Transactional(readOnly = true)
    public List<PeriodStatus> list() {
        var startDate = settings.get().startDate();
        if (startDate == null) {
            return List.of();
        }
        Map<LocalDate, LockRow> locks =
                jdbc.sql("SELECT month, locked_by, locked_at FROM period_lock").query(LockRow.class).list().stream()
                        .collect(Collectors.toMap(LockRow::month, Function.identity()));
        var first = YearMonth.from(startDate);
        var months = new ArrayList<PeriodStatus>();
        for (var month = YearMonth.now(clock.withZone(SHOP_ZONE));
                !month.isBefore(first);
                month = month.minusMonths(1)) {
            var lock = locks.get(month.atDay(1));
            months.add(new PeriodStatus(
                    month.atDay(1),
                    lock != null,
                    lock == null ? null : lock.lockedBy(),
                    lock == null ? null : lock.lockedAt()));
        }
        return months;
    }

    @Transactional
    public void lock(YearMonth month, @Nullable String actor) {
        jdbc.sql("INSERT INTO period_lock (month, locked_by) VALUES (:month, :by) ON CONFLICT (month) DO NOTHING")
                .param("month", month.atDay(1))
                .param("by", actor)
                .update();
    }

    @Transactional
    public void unlock(YearMonth month) {
        jdbc.sql("DELETE FROM period_lock WHERE month = :month")
                .param("month", month.atDay(1))
                .update();
    }
}

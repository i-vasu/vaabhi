package com.vaabhi.backup;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Backup health for the admin card, the year-end checklist and the auditor pack. */
@Service
public class BackupStatusService {

    private static final int RECENT_LIMIT = 35;
    private static final LocalTime MORNING_CUTOVER = LocalTime.of(2, 0);
    private static final String COLUMNS = """
            id, kind, status, started_at, finished_at, size_bytes, object_key, message, notes,
            attachments_copied, drilled_on, created_by
            """;
    private static final RowMapper<BackupRunView> VIEW = (rs, rowNum) -> new BackupRunView(
            rs.getLong("id"),
            rs.getString("kind"),
            rs.getString("status"),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class),
            rs.getObject("size_bytes", Long.class),
            rs.getString("object_key"),
            rs.getString("message"),
            rs.getString("notes"),
            rs.getInt("attachments_copied"),
            rs.getObject("drilled_on", LocalDate.class),
            rs.getString("created_by"));

    private final BackupProperties properties;
    private final JdbcClient jdbc;

    BackupStatusService(BackupProperties properties, JdbcClient jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    public BackupStatus status() {
        return status(Instant.now());
    }

    public BackupStatus status(Instant now) {
        return new BackupStatus(
                properties.configured(),
                properties.locationLabel(),
                properties.s3Bucket(),
                last("DB", true),
                last("CHECK", false),
                last("DRILL", false),
                missedLastNight(now, newestOkFinished()),
                missedDaysLast7(now),
                recent());
    }

    /**
     * True when no OK database run finished since the cutoff. Before 02:00 IST the night in question is the one
     * that started yesterday, so a late 01:00 run still counts; from 02:00 it is today's.
     */
    static boolean missedLastNight(Instant now, @Nullable Instant lastOk) {
        var local = now.atZone(BackupKeys.IST);
        var today = local.toLocalDate();
        var cutoffDay = local.toLocalTime().isBefore(MORNING_CUTOVER) ? today.minusDays(1) : today;
        var cutoff = cutoffDay.atStartOfDay(BackupKeys.IST).toInstant();
        return lastOk == null || lastOk.isBefore(cutoff);
    }

    /** How many of the last seven nights (waking days) have no OK database run. */
    int missedDaysLast7(Instant now) {
        var local = now.atZone(BackupKeys.IST);
        var today = local.toLocalDate();
        var lastDay = local.toLocalTime().isBefore(MORNING_CUTOVER) ? today.minusDays(1) : today;
        var days = new ArrayList<LocalDate>();
        for (var i = 0; i < 7; i++) {
            days.add(lastDay.minusDays(i));
        }
        var from = days.get(6).atStartOfDay(BackupKeys.IST).toInstant();
        var okDays = jdbc.sql("""
                        SELECT finished_at
                        FROM backup_run
                        WHERE kind = 'DB' AND status = 'OK' AND finished_at >= :from
                        """)
                .param("from", Timestamp.from(from))
                .query(Timestamp.class)
                .list()
                .stream()
                .map(timestamp -> timestamp.toInstant().atZone(BackupKeys.IST).toLocalDate())
                .collect(Collectors.toSet());
        return (int) days.stream().filter(day -> !okDays.contains(day)).count();
    }

    private @Nullable BackupRunView last(String kind, boolean finishedOnly) {
        var sql = "SELECT " + COLUMNS + " FROM backup_run WHERE kind = :kind"
                + (finishedOnly ? " AND status IN ('OK', 'FAILED')" : "")
                + " ORDER BY started_at DESC LIMIT 1";
        return jdbc.sql(sql).param("kind", kind).query(VIEW).optional().orElse(null);
    }

    private @Nullable Instant newestOkFinished() {
        return jdbc.sql("""
                        SELECT finished_at
                        FROM backup_run
                        WHERE kind = 'DB' AND status = 'OK' AND finished_at IS NOT NULL
                        ORDER BY started_at DESC
                        LIMIT 1
                        """)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant)
                .orElse(null);
    }

    private List<BackupRunView> recent() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM backup_run ORDER BY started_at DESC LIMIT " + RECENT_LIMIT)
                .query(VIEW)
                .list();
    }
}

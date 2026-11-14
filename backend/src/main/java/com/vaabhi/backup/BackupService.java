package com.vaabhi.backup;

import com.vaabhi.shared.storage.ObjectStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The nightly backup of the books database: dump it, hash it, upload it under an Object Lock and check what the
 * bucket holds. The 01:30 retry only runs when the morning has no OK run yet. A run older than two hours counts as
 * crashed and is expired, so one bad night never blocks the next. The temp dump is deleted on every path.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final Duration STALE_AFTER = Duration.ofHours(2);
    private static final int MESSAGE_LIMIT = 500;
    private static final int NOTES_LIMIT = 2000;

    private final BackupProperties properties;
    private final BackupRunStore runs;
    private final DatabaseDumper dumper;
    private final S3BackupStore store;
    private final AttachmentCopier copier;
    private final ObjectStorage objects;
    private final JdbcClient jdbc;

    BackupService(
            BackupProperties properties,
            BackupRunStore runs,
            DatabaseDumper dumper,
            S3BackupStore store,
            AttachmentCopier copier,
            ObjectStorage objects,
            JdbcClient jdbc) {
        this.properties = properties;
        this.runs = runs;
        this.dumper = dumper;
        this.store = store;
        this.copier = copier;
        this.objects = objects;
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")
    public void nightly() {
        runScheduled(false);
    }

    @Scheduled(cron = "0 30 1 * * *", zone = "Asia/Kolkata")
    public void retry() {
        var outcome = runScheduled(true);
        if (outcome != null && outcome.failed()) {
            alert(outcome.message());
        }
    }

    /** Runs a backup now and returns the run id; conflicts while one is running or when backups are not set up. */
    public long runNow(@Nullable String actor) {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Backups aren't set up");
        }
        return run(actor).id();
    }

    /** The owner records a manual restore drill, which is what proves a backup can be put back. */
    public void recordDrill(LocalDate drilledOn, @Nullable String note, @Nullable String actor) {
        var message = note == null || note.isBlank() ? null : note.trim();
        jdbc.sql("""
                        INSERT INTO backup_run
                            (kind, status, started_at, finished_at, drilled_on, message, created_by)
                        VALUES ('DRILL', 'OK', now(), now(), :drilledOn, :message, :actor)
                        """)
                .param("drilledOn", drilledOn)
                .param("message", message)
                .param("actor", actor)
                .update();
    }

    private @Nullable RunOutcome runScheduled(boolean onlyIfMissing) {
        try {
            if (!properties.configured()) {
                return null;
            }
            if (onlyIfMissing
                    && runs.hasOkSince(LocalDate.now(BackupKeys.IST)
                            .atStartOfDay(BackupKeys.IST)
                            .toInstant())) {
                return null;
            }
            return run(null);
        } catch (ResponseStatusException e) {
            log.warn("Backup not started: {}", e.getReason());
            return null;
        } catch (Exception e) {
            log.error("Backup not started", e);
            return null;
        }
    }

    private RunOutcome run(@Nullable String actor) {
        runs.expireStale(STALE_AFTER);
        var id = runs.start("DB", actor);
        if (id < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A backup is running");
        }
        Path dump = null;
        try {
            var clientMajor = dumper.clientMajor();
            var serverMajor = serverVersion().major();
            if (clientMajor < serverMajor) {
                throw new IllegalStateException("pg_dump " + clientMajor + " can't back up Postgres " + serverMajor
                        + "; update the backend image");
            }
            dump = Files.createTempFile("vaabhi-", ".dump");
            dumper.dump(dump);
            var sha = sha256Hex(dump);
            var size = Files.size(dump);
            var now = Instant.now();
            var ist = ZonedDateTime.ofInstant(now, BackupKeys.IST);
            var key = BackupKeys.daily(ist);
            store.put(key, dump, sha, BackupKeys.dailyRetainUntil(now));
            String monthlyKey = null;
            if (BackupKeys.isMonthEnd(ist.toLocalDate())) {
                monthlyKey = BackupKeys.monthly(ist.toLocalDate());
                store.put(monthlyKey, dump, sha, BackupKeys.longRetainUntil(now));
            }
            verifyUpload(key, size, sha);
            if (monthlyKey != null) {
                verifyUpload(monthlyKey, size, sha);
            }
            var copied = 0;
            String notes = null;
            if (objects.configured()) {
                var result = copier.copyNew();
                copied = result.copied();
                notes = joinProblems(result.problems());
            }
            runs.ok(id, size, sha, key, monthlyKey, copied, notes);
            return new RunOutcome(id, false, null);
        } catch (Exception e) {
            var message = failureOf(e);
            log.warn("Backup run {} failed: {}", id, message);
            runs.fail(id, message);
            return new RunOutcome(id, true, message);
        } finally {
            delete(dump);
        }
    }

    /**
     * The alert is a WhatsApp message. The sender only carries approved templates and there is no backup template
     * yet, so a failed retry is logged with the same text the message would have.
     */
    private void alert(@Nullable String message) {
        var phone = properties.alertPhone();
        if (phone == null || phone.isBlank()) {
            return;
        }
        log.warn("Backup alert skipped: no template; backup failed: {}", message);
    }

    private static @Nullable String joinProblems(List<String> problems) {
        if (problems.isEmpty()) {
            return null;
        }
        var joined = String.join("; ", problems);
        return joined.length() <= NOTES_LIMIT ? joined : joined.substring(0, NOTES_LIMIT);
    }

    /** The server's major version, from its own direct (non-pooled) URL, not the app's pooler. */
    private PgVersion serverVersion() {
        var url = Objects.requireNonNull(properties.databaseUrl(), "vaabhi.backup.database-url is not set");
        try (var connection =
                        DriverManager.getConnection(url, properties.databaseUser(), properties.databasePassword());
                var statement = connection.createStatement();
                var result = statement.executeQuery("SHOW server_version_num")) {
            if (!result.next()) {
                throw new IllegalStateException("Could not read the Postgres version");
            }
            return PgVersion.ofServerNum(Integer.parseInt(result.getString(1)));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the Postgres version: " + e.getMessage(), e);
        }
    }

    private void verifyUpload(String key, long size, String sha) {
        var expected = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha));
        var info = store.head(key).orElseThrow(() -> new IllegalStateException("Upload check failed"));
        if (info.sizeBytes() != size || (info.sha256Base64() != null && !expected.equals(info.sha256Base64()))) {
            throw new IllegalStateException("Upload check failed");
        }
    }

    static String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        try (var in = Files.newInputStream(file)) {
            var buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String failureOf(Throwable error) {
        var message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        var line = message.replaceAll("\\s+", " ").trim();
        return line.length() <= MESSAGE_LIMIT ? line : line.substring(0, MESSAGE_LIMIT);
    }

    private static void delete(@Nullable Path dump) {
        if (dump == null) {
            return;
        }
        try {
            Files.deleteIfExists(dump);
        } catch (IOException e) {
            log.warn("Could not delete the temporary dump {}", dump, e);
        }
    }

    private record RunOutcome(
            long id, boolean failed, @Nullable String message) {}
}

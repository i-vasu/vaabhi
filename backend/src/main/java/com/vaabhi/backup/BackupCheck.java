package com.vaabhi.backup;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The monthly check: download the newest good dump, verify its checksum and prove {@code pg_restore} reads a
 * complete set of tables. Records a CHECK run either way; it never touches the books.
 */
@Service
class BackupCheck {

    private static final Logger log = LoggerFactory.getLogger(BackupCheck.class);
    private static final long LIST_TIMEOUT_MINUTES = 2;
    private static final List<String> REQUIRED_TABLES =
            List.of("journal_entry", "journal_line", "account", "invoice", "supplier_bill", "party");

    private final BackupProperties properties;
    private final S3BackupStore store;
    private final JdbcClient jdbc;

    BackupCheck(BackupProperties properties, S3BackupStore store, JdbcClient jdbc) {
        this.properties = properties;
        this.store = store;
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Kolkata")
    public void monthly() {
        if (!properties.configured()) {
            return;
        }
        var newest = jdbc.sql("""
                        SELECT object_key, sha256
                        FROM backup_run
                        WHERE kind = 'DB' AND status = 'OK' AND object_key IS NOT NULL AND sha256 IS NOT NULL
                        ORDER BY started_at DESC
                        LIMIT 1
                        """)
                .query((rs, rowNum) -> new Dump(rs.getString("object_key"), rs.getString("sha256")))
                .optional()
                .orElse(null);
        if (newest == null) {
            return;
        }
        Path file = null;
        try {
            file = Files.createTempFile("vaabhi-check-", ".dump");
            store.get(newest.key(), file);
            if (!BackupService.sha256Hex(file).equals(newest.sha256())) {
                throw new IllegalStateException("Downloaded dump does not match its SHA-256");
            }
            var missing = missingTables(file);
            if (!missing.isEmpty()) {
                throw new IllegalStateException("No TABLE DATA for " + String.join(", ", missing));
            }
            recordRun("OK", null);
        } catch (Exception e) {
            var message = BackupService.failureOf(e);
            log.warn("Monthly backup check failed: {}", message);
            recordRun("FAILED", message);
        } finally {
            delete(file);
        }
    }

    /** {@code pg_restore --list} reads the dump's table of contents, so a truncated file shows up here. */
    private List<String> missingTables(Path file) throws IOException {
        var process = new ProcessBuilder(properties.pgRestorePath(), "--list", file.toString())
                .redirectErrorStream(true)
                .start();
        var output = new StringBuilder();
        var drain = new Thread(() -> drain(process, output), "pg_restore-list");
        drain.setDaemon(true);
        drain.start();
        try {
            if (!process.waitFor(LIST_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("pg_restore --list timed out");
            }
            drain.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running pg_restore --list", e);
        }
        var text = output.toString();
        if (process.exitValue() != 0) {
            throw new IOException("pg_restore --list failed: " + text);
        }
        var lines = text.lines().toList();
        return REQUIRED_TABLES.stream()
                .filter(table -> lines.stream().noneMatch(line -> line.contains("TABLE DATA public " + table)))
                .toList();
    }

    private void recordRun(String status, @Nullable String message) {
        jdbc.sql("""
                        INSERT INTO backup_run (kind, status, finished_at, message)
                        VALUES ('CHECK', :status, now(), :message)
                        """).param("status", status).param("message", message).update();
    }

    private static void drain(Process process, StringBuilder output) {
        try (var reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            var buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                output.append(buffer, 0, read);
            }
        } catch (IOException ignored) {
            // The process is gone; whatever arrived is enough to decide.
        }
    }

    private static void delete(@Nullable Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete the temporary check file {}", file, e);
        }
    }

    private record Dump(String key, String sha256) {}
}

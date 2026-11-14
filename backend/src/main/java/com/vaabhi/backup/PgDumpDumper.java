package com.vaabhi.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * {@code pg_dump} in custom format. The password travels only in the child's {@code PGPASSWORD}, never on the
 * command line, and stderr is kept short so a failure fits the run's message column.
 */
@Component
class PgDumpDumper implements DatabaseDumper {

    private static final long DUMP_TIMEOUT_MINUTES = 20;
    private static final long VERSION_TIMEOUT_SECONDS = 30;
    private static final int STDERR_KEEP = 2048;
    private static final int MESSAGE_LIMIT = 500;

    private final BackupProperties properties;

    private volatile Integer clientMajor;

    PgDumpDumper(BackupProperties properties) {
        this.properties = properties;
    }

    @Override
    public void dump(Path into) throws IOException {
        var builder = new ProcessBuilder(command(into)).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        var password = properties.databasePassword();
        if (password != null && !password.isBlank()) {
            builder.environment().put("PGPASSWORD", password);
        }
        var process = builder.start();
        var stderr = new StringBuilder();
        var drain = new Thread(() -> stderr.append(readLast(process.getErrorStream())), "pg_dump-stderr");
        drain.setDaemon(true);
        drain.start();
        boolean finished;
        try {
            finished = process.waitFor(DUMP_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running pg_dump", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("pg_dump timed out after " + DUMP_TIMEOUT_MINUTES + " minutes");
        }
        try {
            drain.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (process.exitValue() != 0) {
            var detail = oneLine(stderr.toString());
            throw new IOException(
                    "pg_dump failed: " + (detail.isEmpty() ? "exit code " + process.exitValue() : detail));
        }
    }

    @Override
    public int clientMajor() {
        var cached = clientMajor;
        if (cached != null) {
            return cached;
        }
        try {
            var process = new ProcessBuilder(properties.pgDumpPath(), "--version")
                    .redirectErrorStream(true)
                    .start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("pg_dump --version timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("pg_dump --version failed: " + oneLine(output));
            }
            var major =
                    PgVersion.ofClient(output.lines().findFirst().orElse("")).major();
            clientMajor = major;
            return major;
        } catch (IOException e) {
            throw new IllegalStateException("Could not run " + properties.pgDumpPath() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running pg_dump --version", e);
        }
    }

    /** The command line, with the connection as a libpq URI that carries the user but never the password. */
    List<String> command(Path into) {
        return List.of(
                properties.pgDumpPath(),
                "--format=custom",
                "--compress=6",
                "--no-owner",
                "--no-privileges",
                "--file",
                into.toString(),
                "--dbname",
                libpqUri());
    }

    private String libpqUri() {
        var url = Objects.requireNonNull(properties.databaseUrl(), "vaabhi.backup.database-url is not set")
                .trim();
        if (url.startsWith("jdbc:")) {
            url = url.substring("jdbc:".length());
        }
        var scheme = url.indexOf("://");
        if (scheme < 0) {
            throw new IllegalStateException("vaabhi.backup.database-url is not a JDBC Postgres URL");
        }
        var authorityStart = scheme + 3;
        var authorityEnd = url.length();
        for (var i = authorityStart; i < url.length(); i++) {
            var c = url.charAt(i);
            if (c == '/' || c == '?') {
                authorityEnd = i;
                break;
            }
        }
        if (url.substring(authorityStart, authorityEnd).contains("@")) {
            return url;
        }
        var user = Objects.requireNonNull(properties.databaseUser(), "vaabhi.backup.database-user is not set");
        return url.substring(0, authorityStart) + user + "@" + url.substring(authorityStart);
    }

    /** Keeps the last {@link #STDERR_KEEP} characters, so a chatty failure still leaves a usable message. */
    private static String readLast(InputStream stderr) {
        var kept = new StringBuilder();
        try (var reader = new InputStreamReader(stderr, StandardCharsets.UTF_8)) {
            var buffer = new char[STDERR_KEEP];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                kept.append(buffer, 0, read);
                if (kept.length() > STDERR_KEEP) {
                    kept.delete(0, kept.length() - STDERR_KEEP);
                }
            }
        } catch (IOException ignored) {
            // The process is gone; whatever arrived is enough for the message.
        }
        return kept.toString();
    }

    private static String oneLine(String text) {
        var line = text.replaceAll("\\s+", " ").trim();
        return line.length() <= MESSAGE_LIMIT ? line : line.substring(0, MESSAGE_LIMIT);
    }
}

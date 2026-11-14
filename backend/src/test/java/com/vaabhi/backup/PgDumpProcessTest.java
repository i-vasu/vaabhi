package com.vaabhi.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Runs the dumper against small shell scripts that stand in for {@code pg_dump}. */
@DisabledOnOs(OS.WINDOWS)
class PgDumpProcessTest {

    @TempDir
    Path dir;

    private static BackupProperties properties(String pgDump, String databaseUrl, String user) {
        return new BackupProperties(
                true,
                databaseUrl,
                user,
                "s3cret",
                null,
                null,
                "vaabhi-backups",
                "key",
                "secret",
                null,
                null,
                null,
                pgDump,
                null,
                null);
    }

    private String script(String name, String body) throws IOException {
        var file = dir.resolve(name);
        Files.writeString(file, "#!/bin/sh\n" + body + "\n");
        assertThat(file.toFile().setExecutable(true)).isTrue();
        return file.toString();
    }

    private PgDumpDumper dumper(String body) throws IOException {
        return new PgDumpDumper(
                properties(script("pg_dump", body), "jdbc:postgresql://localhost:5432/vaabhi", "vaabhi"));
    }

    @Test
    void readsTheClientVersionOnceAndRemembersIt() throws IOException {
        var counter = dir.resolve("calls");
        var dumper = dumper("echo x >> " + counter + "\necho 'pg_dump (PostgreSQL) 17.2'");

        assertThat(dumper.clientMajor()).isEqualTo(17);
        assertThat(dumper.clientMajor()).isEqualTo(17);
        assertThat(Files.readAllLines(counter)).hasSize(1);
    }

    @Test
    void reportsAFailingOrMissingVersionCheck() throws IOException {
        assertThatThrownBy(() -> dumper("echo 'library   missing'\nexit 3").clientMajor())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("pg_dump --version failed: library missing");

        var missing = new PgDumpDumper(
                properties(dir.resolve("no-such-pg_dump").toString(), "jdbc:postgresql://localhost/vaabhi", "vaabhi"));
        assertThatThrownBy(missing::clientMajor)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Could not run ");
    }

    @Test
    void dumpsWithThePasswordOnlyInTheEnvironment() throws IOException {
        var dumper = dumper("""
                [ "$PGPASSWORD" = "s3cret" ] || exit 9
                case "$*" in *s3cret*) exit 8 ;; esac
                exit 0""");
        assertThatCode(() -> dumper.dump(dir.resolve("out.dump"))).doesNotThrowAnyException();
    }

    @Test
    void reportsWhatPgDumpSaidWhenItFails() throws IOException {
        assertThatThrownBy(
                        () -> dumper("echo 'connection\n  refused' >&2\nexit 1").dump(dir.resolve("a.dump")))
                .isInstanceOf(IOException.class)
                .hasMessage("pg_dump failed: connection refused");
        assertThatThrownBy(() -> dumper("exit 2").dump(dir.resolve("b.dump")))
                .isInstanceOf(IOException.class)
                .hasMessage("pg_dump failed: exit code 2");
    }

    @Test
    void needsAPostgresUrlAndAUser() {
        assertThatThrownBy(() -> new PgDumpDumper(properties("pg_dump", null, "vaabhi")).command(dir))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("database-url");
        assertThatThrownBy(
                        () -> new PgDumpDumper(properties("pg_dump", "localhost:5432/vaabhi", "vaabhi")).command(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a JDBC Postgres URL");
        assertThatThrownBy(
                        () -> new PgDumpDumper(properties("pg_dump", "jdbc:postgresql://db/vaabhi", null)).command(dir))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("database-user");
        assertThat(new PgDumpDumper(properties("pg_dump", "postgresql://db", "vaabhi")).command(dir))
                .last()
                .isEqualTo("postgresql://vaabhi@db");
    }

    @Test
    void fillsInDefaultsAndKnowsWhenItIsSetUp() {
        var defaults = new BackupProperties(
                false, null, null, null, null, null, null, null, null, null, null, " ", "", null, null);
        assertThat(defaults.lockMode()).isEqualTo(BackupLockMode.NONE);
        assertThat(defaults.checksum()).isEqualTo(BackupChecksumMode.MD5);
        assertThat(defaults.locationLabel()).isEqualTo("AceCloud object storage, India");
        assertThat(defaults.pgDumpPath()).isEqualTo("pg_dump");
        assertThat(defaults.pgRestorePath()).isEqualTo("pg_restore");
        assertThat(defaults.configured()).isFalse();

        var full = new BackupProperties(
                true,
                "jdbc:postgresql://db/v",
                "u",
                "p",
                "https://s3",
                "ap-south-1",
                "b",
                "k",
                "s",
                null,
                null,
                "",
                "",
                "",
                null);
        assertThat(full.configured()).isTrue();
        var noBucket = new BackupProperties(
                true,
                "jdbc:postgresql://db/v",
                "u",
                "p",
                "https://s3",
                "ap-south-1",
                " ",
                "k",
                "s",
                null,
                null,
                "",
                "",
                "",
                null);
        assertThat(noBucket.configured()).isFalse();
    }
}

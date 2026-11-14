package com.vaabhi.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PgDumpDumperTest {

    private static BackupProperties properties(String databaseUrl) {
        return new BackupProperties(
                true,
                databaseUrl,
                "vaabhi",
                "s3cret",
                null,
                null,
                "vaabhi-backups",
                "key",
                "secret",
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void commandNeverCarriesThePassword() {
        var dumper = new PgDumpDumper(properties("jdbc:postgresql://db.example.com:5432/vaabhi?sslmode=require"));

        var command = dumper.command(Path.of("vaabhi.dump"));

        assertThat(command).noneMatch(part -> part.contains("s3cret"));
        assertThat(command).endsWith("--dbname", "postgresql://vaabhi@db.example.com:5432/vaabhi?sslmode=require");
    }

    @Test
    void commandKeepsAUserTheUrlAlreadyHas() {
        var dumper = new PgDumpDumper(properties("jdbc:postgresql://neondb_owner@db.example.com/vaabhi"));

        var command = dumper.command(Path.of("vaabhi.dump"));

        assertThat(command.get(command.size() - 1)).isEqualTo("postgresql://neondb_owner@db.example.com/vaabhi");
    }
}

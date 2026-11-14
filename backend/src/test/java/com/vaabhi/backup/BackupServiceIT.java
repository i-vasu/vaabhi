package com.vaabhi.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.storage.SigV4Signer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The nightly backup against real Postgres and MinIO with object lock: the dump is uploaded and checked, failures
 * leave a FAILED row, a crashed run is expired, a live one blocks a second run, and the temp dump never survives.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, BackupServiceIT.Stubs.class})
class BackupServiceIT {

    private static final String BUCKET = "vaabhi-backups";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String REGION = "us-east-1";
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final int DUMP_BYTES = 10 * 1024;

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
                    "minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @TempDir
    Path tempDir;

    @Autowired
    private BackupService backup;

    @Autowired
    private S3BackupStore store;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StubDumper dumper;

    /** The real {@code pg_dump} is not on every test host; the stub writes bytes, fails or reports a version. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {

        @Bean
        @Primary
        BackupProperties backupProperties(PostgreSQLContainer postgres) {
            return new BackupProperties(
                    true,
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword(),
                    minioEndpoint(),
                    REGION,
                    BUCKET,
                    ACCESS_KEY,
                    SECRET_KEY,
                    BackupLockMode.COMPLIANCE,
                    BackupChecksumMode.SHA256,
                    "MinIO test",
                    "pg_dump",
                    "pg_restore",
                    null);
        }

        @Bean
        @Primary
        StubDumper stubDumper() {
            return new StubDumper();
        }
    }

    static class StubDumper implements DatabaseDumper {

        private volatile int bytes = DUMP_BYTES;
        private volatile int major = 17;
        private volatile IOException failure;

        @Override
        public void dump(Path into) throws IOException {
            var failure = this.failure;
            if (failure != null) {
                throw failure;
            }
            Files.write(into, new byte[bytes]);
        }

        @Override
        public int clientMajor() {
            return major;
        }

        void reset() {
            bytes = DUMP_BYTES;
            major = 17;
            failure = null;
        }
    }

    private static String minioEndpoint() {
        MINIO.start();
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    @BeforeAll
    static void createBucketWithObjectLock() throws Exception {
        var uri = URI.create(minioEndpoint() + "/" + BUCKET);
        var headers = Map.of("x-amz-bucket-object-lock-enabled", "true");
        var request = HttpRequest.newBuilder(uri).PUT(HttpRequest.BodyPublishers.noBody());
        SigV4Signer.sign("PUT", uri, headers, EMPTY_SHA256, REGION, "s3", ACCESS_KEY, SECRET_KEY, Instant.now())
                .forEach(request::header);
        headers.forEach(request::header);
        var response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @BeforeEach
    void cleanRuns() {
        jdbc.sql("DELETE FROM backup_run WHERE kind = 'DB'").update();
        dumper.reset();
    }

    private record RunRow(
            String status,
            @Nullable Long sizeBytes,
            @Nullable String sha256,
            @Nullable String objectKey,
            @Nullable String monthlyKey,
            @Nullable String message) {}

    private RunRow run(long id) {
        return jdbc.sql("""
                        SELECT status, size_bytes, sha256, object_key, monthly_key, message
                        FROM backup_run
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(RunRow.class)
                .single();
    }

    private static Set<Path> dumpFiles() throws IOException {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(file -> {
                        var name = file.getFileName().toString();
                        return name.startsWith("vaabhi-") && name.endsWith(".dump");
                    })
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void runNowUploadsTheDumpAndChecksIt() throws Exception {
        var id = backup.runNow("tester");

        var row = run(id);
        assertThat(row.status()).isEqualTo("OK");
        assertThat(row.sizeBytes()).isEqualTo(DUMP_BYTES);
        assertThat(row.objectKey()).startsWith("db/");
        assertThat(row.message()).isNull();

        var digest = MessageDigest.getInstance("SHA-256").digest(new byte[DUMP_BYTES]);
        assertThat(row.sha256()).isEqualTo(HexFormat.of().formatHex(digest));

        var info = store.head(row.objectKey()).orElseThrow();
        assertThat(info.sizeBytes()).isEqualTo(DUMP_BYTES);
        assertThat(info.sha256Base64()).isEqualTo(Base64.getEncoder().encodeToString(digest));

        var copy = tempDir.resolve("downloaded.dump");
        store.get(row.objectKey(), copy);
        assertThat(Files.size(copy)).isEqualTo(DUMP_BYTES);
    }

    @Test
    void aFailedDumpLeavesAFailedRowAndNoTempFile() throws Exception {
        var before = dumpFiles();
        dumper.failure = new IOException("disk on fire");

        var id = backup.runNow(null);

        var row = run(id);
        assertThat(row.status()).isEqualTo("FAILED");
        assertThat(row.message()).isEqualTo("disk on fire");
        assertThat(dumpFiles()).isEqualTo(before);
    }

    @Test
    void aStaleRunningRunIsExpiredAndTheNextOneProceeds() {
        var stale = jdbc.sql("""
                        INSERT INTO backup_run (kind, status, started_at)
                        VALUES ('DB', 'RUNNING', now() - interval '3 hours')
                        RETURNING id
                        """)
                .query(Long.class)
                .single();

        var id = backup.runNow(null);

        var interrupted = run(stale);
        assertThat(interrupted.status()).isEqualTo("FAILED");
        assertThat(interrupted.message()).isEqualTo("Interrupted");
        assertThat(run(id).status()).isEqualTo("OK");
    }

    @Test
    void aRunningBackupIsAConflict() {
        var running = jdbc.sql("""
                        INSERT INTO backup_run (kind, status, started_at)
                        VALUES ('DB', 'RUNNING', now() - interval '5 minutes')
                        RETURNING id
                        """)
                .query(Long.class)
                .single();

        assertThatThrownBy(() -> backup.runNow(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A backup is running");
        assertThat(run(running).status()).isEqualTo("RUNNING");
    }

    @Test
    void anOlderClientCannotBackUpANewerServer() {
        var serverMajor = Integer.parseInt(
                        jdbc.sql("SELECT current_setting('server_version_num')").query(String.class).single())
                / 10000;
        dumper.major = 15;

        var id = backup.runNow(null);

        var row = run(id);
        assertThat(row.status()).isEqualTo("FAILED");
        assertThat(row.message())
                .isEqualTo("pg_dump 15 can't back up Postgres " + serverMajor + "; update the backend image");
    }
}

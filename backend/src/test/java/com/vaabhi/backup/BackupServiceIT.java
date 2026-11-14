package com.vaabhi.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.storage.ObjectStorage;
import com.vaabhi.shared.storage.SigV4Signer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The nightly backup against real Postgres and MinIO with object lock: the dump is uploaded and checked, failures
 * leave a FAILED row, a crashed run is expired, a live one blocks a second run, attachments are copied once, and
 * the temp files never survive.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, BackupServiceIT.Stubs.class})
class BackupServiceIT {

    private static final String BUCKET = "vaabhi-backups";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String REGION = "us-east-1";
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final int DUMP_BYTES = 10 * 1024;
    private static final RequestPostProcessor OWNER =
            jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
                    "adobe/s3mock:4.7.0") // MinIO images are no longer public
            .withExposedPorts(9090)
            .waitingFor(Wait.forListeningPort());

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

    @Autowired
    private StubObjectStorage objects;

    @Autowired
    private MockMvc mockMvc;

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

        @Bean
        @Primary
        StubObjectStorage stubObjectStorage() {
            return new StubObjectStorage();
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

    /** R2 stand-in: keys put here can be read; any other key reports a missing object. */
    static class StubObjectStorage implements ObjectStorage {

        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public URI presignPut(String key, Duration validFor) {
            throw new UnsupportedOperationException("Not needed by the backup");
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public InputStream open(String key) throws IOException {
            var bytes = objects.get(key);
            if (bytes == null) {
                throw new NoSuchFileException(key);
            }
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        void store(String key, String body) {
            objects.put(key, body.getBytes(StandardCharsets.UTF_8));
        }

        void clear() {
            objects.clear();
        }
    }

    private static String minioEndpoint() {
        MINIO.start();
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9090);
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
    void cleanState() {
        jdbc.sql("DELETE FROM backup_run").update();
        jdbc.sql("DELETE FROM backup_file").update();
        jdbc.sql("DELETE FROM supplier_bill WHERE attachment_key LIKE 'it/%'").update();
        dumper.reset();
        objects.clear();
    }

    private record RunRow(
            String status,
            @Nullable Long sizeBytes,
            @Nullable String sha256,
            @Nullable String objectKey,
            @Nullable String monthlyKey,
            int attachmentsCopied,
            @Nullable String message,
            @Nullable String notes) {}

    private RunRow run(long id) {
        return jdbc.sql("""
                        SELECT status, size_bytes, sha256, object_key, monthly_key, attachments_copied, message, notes
                        FROM backup_run
                        WHERE id = :id
                        """).param("id", id).query(RunRow.class).single();
    }

    /** A supplier bill with (or without) an attachment key, enough for the copier's query. */
    private void supplierBill(String billNo, String attachmentKey) {
        var partyId = jdbc.sql("INSERT INTO party (name, is_supplier) VALUES ('IT Backup Supplier', TRUE) RETURNING id")
                .query(Long.class)
                .single();
        jdbc.sql("""
                        INSERT INTO supplier_bill
                            (party_id, bill_no, bill_date, taxable_rupees, tax_rupees, total_rupees,
                             payable_rupees, attachment_key)
                        VALUES (:party, :billNo, current_date, 100, 18, 118, 118, :key)
                        """)
                .param("party", partyId)
                .param("billNo", billNo)
                .param("key", attachmentKey)
                .update();
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
                        """).query(Long.class).single();

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
                        """).query(Long.class).single();

        assertThatThrownBy(() -> backup.runNow(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A backup is running");
        assertThat(run(running).status()).isEqualTo("RUNNING");
    }

    @Test
    void anOlderClientCannotBackUpANewerServer() {
        var serverMajor = Integer.parseInt(jdbc.sql("SELECT current_setting('server_version_num')")
                        .query(String.class)
                        .single())
                / 10000;
        dumper.major = 15;

        var id = backup.runNow(null);

        var row = run(id);
        assertThat(row.status()).isEqualTo("FAILED");
        assertThat(row.message())
                .isEqualTo("pg_dump 15 can't back up Postgres " + serverMajor + "; update the backend image");
    }

    @Test
    void attachmentsAreCopiedOnceOnly() {
        objects.store("it/bill-1.pdf", "first bill");
        objects.store("it/bill-2.pdf", "second bill");
        supplierBill("IT-1", "it/bill-1.pdf");
        supplierBill("IT-2", "it/bill-2.pdf");

        var first = run(backup.runNow(null));
        assertThat(first.status()).isEqualTo("OK");
        assertThat(first.attachmentsCopied()).isEqualTo(2);
        assertThat(first.notes()).isNull();
        assertThat(store.head("attachments/it/bill-1.pdf")).isPresent();
        assertThat(jdbc.sql("SELECT count(*) FROM backup_file")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);

        var second = run(backup.runNow(null));
        assertThat(second.status()).isEqualTo("OK");
        assertThat(second.attachmentsCopied()).isZero();
    }

    @Test
    void aMissingAttachmentIsNotedAndRetriedNextNight() {
        supplierBill("IT-3", "it/missing.pdf");

        var first = run(backup.runNow(null));
        assertThat(first.status()).isEqualTo("OK");
        assertThat(first.attachmentsCopied()).isZero();
        assertThat(first.notes()).contains("it/missing.pdf").contains("missing in R2");

        var second = run(backup.runNow(null));
        assertThat(second.status()).isEqualTo("OK");
        assertThat(second.notes()).contains("it/missing.pdf");
        assertThat(jdbc.sql("SELECT count(*) FROM backup_file")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void theOwnerRecordsARestoreDrillThroughTheApi() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/backups/drills")
                        .with(OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drilledOn\":\"2026-11-14\",\"note\":\"IT restore drill\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastDrill.drilledOn").value("2026-11-14"))
                .andExpect(jsonPath("$.lastDrill.message").value("IT restore drill"));
    }
}

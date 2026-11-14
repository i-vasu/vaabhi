package com.vaabhi.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.shared.storage.SigV4Signer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The backup bucket over the plain S3 API, against MinIO with and without object lock. */
@Testcontainers(disabledWithoutDocker = true)
class S3BackupStoreIT {

    private static final String BUCKET = "vaabhi-backups";
    private static final String NO_LOCK_BUCKET = "vaabhi-backups-md5";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String REGION = "us-east-1";
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

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

    private static String endpoint;

    @BeforeAll
    static void createBuckets() throws Exception {
        endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        createBucket(BUCKET, Map.of("x-amz-bucket-object-lock-enabled", "true"));
        createBucket(NO_LOCK_BUCKET, Map.of());
    }

    private static void createBucket(String bucket, Map<String, String> headers) throws Exception {
        var uri = URI.create(endpoint + "/" + bucket);
        var request = HttpRequest.newBuilder(uri).PUT(HttpRequest.BodyPublishers.noBody());
        SigV4Signer.sign("PUT", uri, headers, EMPTY_SHA256, REGION, "s3", ACCESS_KEY, SECRET_KEY, Instant.now())
                .forEach(request::header);
        headers.forEach(request::header);
        var response =
                HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    private static S3BackupStore store(String bucket, BackupLockMode lockMode, BackupChecksumMode checksum) {
        return new S3BackupStore(new BackupProperties(
                true,
                "jdbc:postgresql://localhost/vaabhi",
                "vaabhi",
                "secret",
                endpoint,
                REGION,
                bucket,
                ACCESS_KEY,
                SECRET_KEY,
                lockMode,
                checksum,
                "MinIO test",
                "pg_dump",
                "pg_restore",
                null));
    }

    private S3BackupStore store() {
        return store(BUCKET, BackupLockMode.COMPLIANCE, BackupChecksumMode.SHA256);
    }

    @Test
    void putHeadGetRoundTrip() throws Exception {
        var bytes = new byte[3072];
        new Random(42).nextBytes(bytes);
        var file = tempDir.resolve("vaabhi.dump");
        Files.write(file, bytes);
        var key = "db/2026/11/vaabhi-2026-11-14T0100.dump";
        var store = store();

        store.put(key, file, sha256Hex(bytes), Instant.now().plus(Duration.ofDays(35)));

        var info = store.head(key).orElseThrow();
        assertThat(info.sizeBytes()).isEqualTo(3072);
        assertThat(info.sha256Base64()).isEqualTo(Base64.getEncoder().encodeToString(sha256(bytes)));

        var copy = tempDir.resolve("copy.dump");
        store.get(key, copy);
        assertThat(Files.readAllBytes(copy)).isEqualTo(bytes);
    }

    @Test
    void md5ModeUploadsToABucketWithoutObjectLock() throws Exception {
        var bytes = new byte[4096];
        new Random(7).nextBytes(bytes);
        var file = tempDir.resolve("md5.dump");
        Files.write(file, bytes);
        var key = "db/2026/11/vaabhi-2026-11-14T0101.dump";
        var store = store(NO_LOCK_BUCKET, BackupLockMode.NONE, BackupChecksumMode.MD5);

        store.put(key, file, sha256Hex(bytes), Instant.now().plus(Duration.ofDays(35)));

        var info = store.head(key).orElseThrow();
        assertThat(info.sizeBytes()).isEqualTo(4096);

        var copy = tempDir.resolve("md5-copy.dump");
        store.get(key, copy);
        assertThat(Files.readAllBytes(copy)).isEqualTo(bytes);
    }

    @Test
    void missingObjectHasNoHead() {
        assertThat(store().head("db/2026/11/missing.dump")).isEmpty();
    }

    @Test
    void putWithWrongChecksumIsRejected() throws Exception {
        var file = tempDir.resolve("wrong.dump");
        Files.write(file, new byte[] {1, 2, 3});
        var store = store();

        assertThatThrownBy(() -> store.put(
                        "db/2026/11/wrong.dump", file, "0".repeat(64), Instant.now().plus(Duration.ofDays(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("S3 ");
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(sha256(bytes));
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
}

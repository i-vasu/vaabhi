package com.vaabhi.backup;

import com.vaabhi.shared.storage.SigV4Signer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The backup bucket through the plain S3 API: one header-signed PUT per file, no AWS SDK. Path-style URLs
 * work with AWS S3 and with other India S3 providers, so only configuration changes on a move.
 */
@Component
class S3BackupStore {

    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final Duration TRANSFER_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration HEAD_TIMEOUT = Duration.ofSeconds(30);

    private final BackupProperties properties;
    private final HttpClient http;

    S3BackupStore(BackupProperties properties) {
        this.properties = properties;
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * Uploads {@code file}, whose SHA-256 is {@code sha256Hex}, and locks it until {@code retainUntil} where the
     * provider supports Object Lock. The store rejects the body when it does not match the checksum header.
     */
    void put(String key, Path file, String sha256Hex, Instant retainUntil) {
        var uri = uri(key);
        var lockMode = properties.lockMode();
        var headers = new LinkedHashMap<String, String>();
        if (properties.checksum() == BackupChecksumMode.MD5) {
            headers.put("Content-MD5", md5Base64(file));
        } else {
            headers.put("x-amz-checksum-sha256", checksumBase64(sha256Hex));
        }
        if (lockMode != BackupLockMode.NONE) {
            headers.put("x-amz-object-lock-mode", lockMode.name());
            headers.put(
                    "x-amz-object-lock-retain-until-date",
                    retainUntil.truncatedTo(ChronoUnit.SECONDS).toString());
            headers.put("x-amz-server-side-encryption", "AES256");
        }
        HttpRequest.BodyPublisher body;
        try {
            body = HttpRequest.BodyPublishers.ofFile(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
        var request = HttpRequest.newBuilder(uri).PUT(body).timeout(TRANSFER_TIMEOUT);
        headers.forEach(request::header);
        sign("PUT", uri, headers, sha256Hex).forEach(request::header);
        var response = send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("S3 " + response.statusCode() + ": " + first300(response.body()));
        }
    }

    /** Size and checksum of {@code key}, or empty when the object does not exist. */
    Optional<S3ObjectInfo> head(String key) {
        var uri = uri(key);
        var headers = Map.of("x-amz-checksum-mode", "ENABLED");
        var request = HttpRequest.newBuilder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(HEAD_TIMEOUT);
        headers.forEach(request::header);
        sign("HEAD", uri, headers, EMPTY_SHA256).forEach(request::header);
        var response = send(request.build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("S3 " + response.statusCode());
        }
        var size = response.headers()
                .firstValueAsLong("Content-Length")
                .orElseThrow(() -> new IllegalStateException("S3 HEAD " + key + " returned no Content-Length"));
        return Optional.of(new S3ObjectInfo(
                size, response.headers().firstValue("x-amz-checksum-sha256").orElse(null)));
    }

    /** Downloads {@code key} into {@code into}, replacing the file. */
    void get(String key, Path into) {
        var uri = uri(key);
        var request = HttpRequest.newBuilder(uri).GET().timeout(TRANSFER_TIMEOUT);
        sign("GET", uri, Map.of(), EMPTY_SHA256).forEach(request::header);
        var response = send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("S3 " + response.statusCode() + ": "
                        + first300(new String(body.readNBytes(300), StandardCharsets.UTF_8)));
            }
            Files.copy(body, into, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + into, e);
        }
    }

    private Map<String, String> sign(String method, URI uri, Map<String, String> headers, String payloadSha256Hex) {
        return SigV4Signer.sign(
                method,
                uri,
                headers,
                payloadSha256Hex,
                Objects.requireNonNull(properties.s3Region(), "vaabhi.backup.s3-region is not set"),
                "s3",
                Objects.requireNonNull(properties.s3AccessKeyId(), "vaabhi.backup.s3-access-key-id is not set"),
                Objects.requireNonNull(properties.s3SecretAccessKey(), "vaabhi.backup.s3-secret-access-key is not set"),
                Instant.now());
    }

    private URI uri(String key) {
        var endpoint = Objects.requireNonNull(properties.s3Endpoint(), "vaabhi.backup.s3-endpoint is not set");
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        var bucket = Objects.requireNonNull(properties.s3Bucket(), "vaabhi.backup.s3-bucket is not set");
        return URI.create(endpoint + "/" + bucket + "/" + key);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return http.send(request, handler);
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach the backup bucket", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while talking to the backup bucket", e);
        }
    }

    /** S3 takes the checksum as Base64 of the raw 32-byte digest, not of its hex text. */
    private static String checksumBase64(String sha256Hex) {
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256Hex));
    }

    /** {@code Content-MD5} is Base64 of the raw 16-byte MD5 digest of the file, streamed rather than loaded. */
    // Sonar java:S4790: S3 requires Content-MD5 on object-lock uploads; it checks integrity, it is not security.
    @SuppressWarnings("java:S4790")
    private static String md5Base64(Path file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
        try (var in = Files.newInputStream(file)) {
            var buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    private static String first300(String body) {
        return body.length() <= 300 ? body : body.substring(0, 300);
    }
}

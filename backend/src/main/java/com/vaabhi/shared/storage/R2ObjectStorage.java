package com.vaabhi.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cloudflare R2 through its S3-compatible endpoint. The server mostly signs URLs and makes tiny HEAD/DELETE
 * calls; photo uploads go straight from the admin's browser to R2. The server itself only copies Instagram covers,
 * makes JPEG copies of product photos for the Meta catalogue and reads the occasional bill attachment for the
 * backup.
 */
@Component
@EnableConfigurationProperties(MediaStorageProperties.class)
class R2ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(R2ObjectStorage.class);
    private static final Duration SERVER_CALL_VALIDITY = Duration.ofMinutes(1);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final MediaStorageProperties properties;
    private final HttpClient http;
    private final Clock clock;

    @Autowired
    R2ObjectStorage(MediaStorageProperties properties) {
        this(
                properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Clock.systemUTC());
    }

    R2ObjectStorage(MediaStorageProperties properties, HttpClient http, Clock clock) {
        this.properties = properties;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public URI presignPut(String key, Duration validFor) {
        return presign("PUT", key, validFor);
    }

    @Override
    public boolean exists(String key) {
        var request = HttpRequest.newBuilder(presign("HEAD", key, SERVER_CALL_VALIDITY))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach photo storage", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking photo storage", e);
        }
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        var request = HttpRequest.newBuilder(presign("PUT", key, SERVER_CALL_VALIDITY))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .header("Content-Type", contentType)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            var status =
                    http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 300) {
                throw new IllegalStateException("Photo storage refused " + key + " (" + status + ")");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach photo storage", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while uploading to photo storage", e);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        if (!configured()) {
            throw new IllegalStateException("Photo storage is not configured");
        }
        var uri = URI.create(
                "https://" + properties.accountId() + ".r2.cloudflarestorage.com/" + properties.bucket() + "/" + key);
        var signedHeaders = SigV4Signer.sign(
                "GET",
                uri,
                Map.of(),
                EMPTY_SHA256,
                "auto",
                "s3",
                Objects.requireNonNull(properties.accessKeyId()),
                Objects.requireNonNull(properties.secretAccessKey()),
                clock.instant());
        var request = HttpRequest.newBuilder(uri).GET().timeout(READ_TIMEOUT);
        signedHeaders.forEach(request::header);
        HttpResponse<InputStream> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading photo storage", e);
        }
        if (response.statusCode() == 404) {
            response.body().close();
            throw new NoSuchFileException(key);
        }
        if (response.statusCode() >= 300) {
            try (var body = response.body()) {
                throw new IllegalStateException("Photo storage refused " + key + " (" + response.statusCode() + "): "
                        + new String(body.readNBytes(300), StandardCharsets.UTF_8));
            }
        }
        return response.body();
    }

    @Override
    public byte[] get(String key) {
        var request = HttpRequest.newBuilder(presign("GET", key, SERVER_CALL_VALIDITY))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Photo storage returned " + response.statusCode() + " for " + key);
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach photo storage", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading from photo storage", e);
        }
    }

    @Override
    public void delete(String key) {
        var request = HttpRequest.newBuilder(presign("DELETE", key, SERVER_CALL_VALIDITY))
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            var status =
                    http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 300 && status != 404) {
                log.warn("Deleting {} from photo storage returned {}", key, status);
            }
        } catch (IOException e) {
            log.warn("Could not delete {} from photo storage", key, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while deleting {} from photo storage", key);
        }
    }

    private URI presign(String method, String key, Duration validFor) {
        if (!configured()) {
            throw new IllegalStateException("Photo storage is not configured");
        }
        var presigner = new SigV4Presigner(
                "https",
                properties.accountId() + ".r2.cloudflarestorage.com",
                "auto",
                Objects.requireNonNull(properties.accessKeyId()),
                Objects.requireNonNull(properties.secretAccessKey()));
        return presigner.presign(method, "/" + properties.bucket() + "/" + key, validFor, clock.instant());
    }
}

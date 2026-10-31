package com.vaabhi.shared.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cloudflare R2 through its S3-compatible endpoint. The server only signs URLs and makes tiny HEAD/DELETE
 * calls; photo bytes go straight from the admin's browser to R2, never through the VPS.
 */
@Component
@EnableConfigurationProperties(MediaStorageProperties.class)
class R2ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(R2ObjectStorage.class);
    private static final Duration SERVER_CALL_VALIDITY = Duration.ofMinutes(1);

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

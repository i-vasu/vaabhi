package com.vaabhi.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class R2ObjectStorageTest {

    private static final MediaStorageProperties CONFIGURED =
            new MediaStorageProperties("acc123", "key", "secret", "vaabhi-media");

    private final HttpClient http = mock(HttpClient.class);
    private final R2ObjectStorage storage =
            new R2ObjectStorage(CONFIGURED, http, Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC));

    @SuppressWarnings("unchecked")
    private void respond(int status) throws Exception {
        HttpResponse<Void> response = mock(HttpResponse.class);
        given(response.statusCode()).willReturn(status);
        given(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .willReturn(response);
    }

    @Test
    void presignsPutUrlsForTheBucketOnR2() {
        var url = storage.presignPut("products/1/abc/400.webp", Duration.ofMinutes(15));

        assertThat(url.getHost()).isEqualTo("acc123.r2.cloudflarestorage.com");
        assertThat(url.getPath()).isEqualTo("/vaabhi-media/products/1/abc/400.webp");
        assertThat(url.getQuery())
                .contains("X-Amz-Expires=900")
                .contains("X-Amz-Date=20261031T100000Z")
                .contains("auto/s3/aws4_request");
        assertThat(storage.configured()).isTrue();
    }

    @Test
    void checksExistenceWithASignedHead() throws Exception {
        respond(200);
        assertThat(storage.exists("k")).isTrue();
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("HEAD");

        respond(404);
        assertThat(storage.exists("k")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsUnreachableStorage() throws Exception {
        given(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .willThrow(new IOException("down"));
        assertThatThrownBy(() -> storage.exists("k")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> storage.delete("k")).doesNotThrowAnyException();
    }

    @Test
    void deletesBestEffort() throws Exception {
        respond(500);
        assertThatCode(() -> storage.delete("k")).doesNotThrowAnyException();
        respond(204);
        storage.delete("k");
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, org.mockito.Mockito.times(2)).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("DELETE");
    }

    @Test
    void uploadsFromTheServerWithASignedPut() throws Exception {
        respond(200);
        storage.put("instagram/1.jpg", new byte[] {1, 2, 3}, "image/jpeg");

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("PUT");
        assertThat(request.getValue().uri().getPath()).isEqualTo("/vaabhi-media/instagram/1.jpg");
        assertThat(request.getValue().headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(request.getValue().headers().firstValue("Cache-Control"))
                .hasValue("public, max-age=31536000, immutable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsAFailedServerUpload() throws Exception {
        respond(403);
        assertThatThrownBy(() -> storage.put("k", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refused k (403)");

        given(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .willThrow(new IOException("down"));
        assertThatThrownBy(() -> storage.put("k", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not reach photo storage");
    }

    @Test
    void otherStoragesRefuseServerUploads() {
        ObjectStorage readOnly = new ObjectStorage() {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public java.net.URI presignPut(String key, Duration validFor) {
                return java.net.URI.create("https://r2.test/" + key);
            }

            @Override
            public boolean exists(String key) {
                return false;
            }

            @Override
            public void delete(String key) {}
        };
        assertThatThrownBy(() -> readOnly.put("k", new byte[0], "image/jpeg"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> readOnly.get("k")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadsFromTheServerWithASignedGet() throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        given(response.statusCode()).willReturn(200);
        given(response.body()).willReturn(new byte[] {7, 8});
        given(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .willReturn(response);

        assertThat(storage.get("products/a/1600.webp")).isEqualTo(new byte[] {7, 8});

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("GET");
        assertThat(request.getValue().uri().getPath()).isEqualTo("/vaabhi-media/products/a/1600.webp");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsAFailedDownload() throws Exception {
        respond(404);
        assertThatThrownBy(() -> storage.get("k"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned 404 for k");

        given(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .willThrow(new IOException("down"));
        assertThatThrownBy(() -> storage.get("k"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not reach photo storage");

        given(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .willThrow(new InterruptedException("stop"));
        assertThatThrownBy(() -> storage.get("k"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Interrupted while downloading from photo storage");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void refusesToSignWithoutCredentials() {
        var unconfigured = new R2ObjectStorage(new MediaStorageProperties(null, "", null, null));
        assertThat(unconfigured.configured()).isFalse();
        assertThatThrownBy(() -> unconfigured.presignPut("k", Duration.ofMinutes(1)))
                .hasMessageContaining("not configured");
    }

    @Test
    void defaultsTheBucket() {
        assertThat(new MediaStorageProperties(null, null, null, " ").bucket()).isEqualTo("vaabhi-media");
    }
}

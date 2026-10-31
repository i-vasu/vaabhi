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

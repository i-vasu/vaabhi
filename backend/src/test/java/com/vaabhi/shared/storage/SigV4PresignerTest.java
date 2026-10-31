package com.vaabhi.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SigV4PresignerTest {

    /**
     * The example from the AWS documentation "Authenticating Requests: Using Query Parameters (AWS Signature
     * Version 4)": GET /test.txt on examplebucket, 24 hours, 2013-05-24.
     */
    @Test
    void matchesTheAwsDocumentationExample() {
        var presigner = new SigV4Presigner(
                "https",
                "examplebucket.s3.amazonaws.com",
                "us-east-1",
                "AKIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");

        var url = presigner.presign("GET", "/test.txt", Duration.ofDays(1), Instant.parse("2013-05-24T00:00:00Z"));

        assertThat(url.toString())
                .isEqualTo("https://examplebucket.s3.amazonaws.com/test.txt"
                        + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                        + "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request"
                        + "&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host"
                        + "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404");
    }

    @Test
    void encodesLikeSigV4() {
        assertThat(SigV4Presigner.encode("a b/ü~", false)).isEqualTo("a%20b/%C3%BC~");
        assertThat(SigV4Presigner.encode("a/b", true)).isEqualTo("a%2Fb");
    }
}

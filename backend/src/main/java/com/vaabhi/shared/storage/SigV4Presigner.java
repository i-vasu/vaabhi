package com.vaabhi.shared.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * AWS Signature Version 4 query-string presigning for S3-compatible storage (Cloudflare R2 uses region
 * {@code auto}). Only the {@code host} header is signed, so browsers may send their own Content-Type and
 * Cache-Control. Written out instead of pulling in the AWS SDK, which would cost the 1 GB VPS ~40 MB of heap.
 */
final class SigV4Presigner {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";

    private final String scheme;
    private final String host;
    private final String region;
    private final String accessKeyId;
    private final String secretAccessKey;

    SigV4Presigner(String scheme, String host, String region, String accessKeyId, String secretAccessKey) {
        this.scheme = scheme;
        this.host = host;
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    /** A URL that allows {@code method} on {@code path} (e.g. /bucket/key) until {@code expires} after {@code now}. */
    URI presign(String method, String path, Duration expires, Instant now) {
        var amzDate = SigV4Signer.AMZ_DATE.format(now);
        var date = amzDate.substring(0, 8);
        var scope = date + "/" + region + "/s3/aws4_request";
        var canonicalPath = encode(path, false);

        Map<String, String> query = new TreeMap<>();
        query.put("X-Amz-Algorithm", ALGORITHM);
        query.put("X-Amz-Credential", accessKeyId + "/" + scope);
        query.put("X-Amz-Date", amzDate);
        query.put("X-Amz-Expires", Long.toString(expires.toSeconds()));
        query.put("X-Amz-SignedHeaders", "host");
        var canonicalQuery = query.entrySet().stream()
                .map(e -> encode(e.getKey(), true) + "=" + encode(e.getValue(), true))
                .collect(Collectors.joining("&"));

        var canonicalRequest = String.join(
                "\n", method, canonicalPath, canonicalQuery, "host:" + host, "", "host", "UNSIGNED-PAYLOAD");
        var stringToSign = String.join(
                "\n", ALGORITHM, amzDate, scope, SigV4Signer.HEX.formatHex(SigV4Signer.sha256(canonicalRequest)));

        var key = SigV4Signer.hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
        key = SigV4Signer.hmac(key, region);
        key = SigV4Signer.hmac(key, "s3");
        key = SigV4Signer.hmac(key, "aws4_request");
        var signature = SigV4Signer.HEX.formatHex(SigV4Signer.hmac(key, stringToSign));

        return URI.create(
                scheme + "://" + host + canonicalPath + "?" + canonicalQuery + "&X-Amz-Signature=" + signature);
    }

    /** RFC 3986 encoding as SigV4 requires; slashes are kept in paths. */
    static String encode(String value, boolean encodeSlash) {
        return SigV4Signer.encode(value, encodeSlash);
    }
}

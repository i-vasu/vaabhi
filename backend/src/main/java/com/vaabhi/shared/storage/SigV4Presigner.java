package com.vaabhi.shared.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS Signature Version 4 query-string presigning for S3-compatible storage (Cloudflare R2 uses region
 * {@code auto}). Only the {@code host} header is signed, so browsers may send their own Content-Type and
 * Cache-Control. Written out instead of pulling in the AWS SDK, which would cost the 1 GB VPS ~40 MB of heap.
 */
final class SigV4Presigner {

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final HexFormat HEX = HexFormat.of();
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
        var amzDate = AMZ_DATE.format(now);
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
        var stringToSign = String.join("\n", ALGORITHM, amzDate, scope, HEX.formatHex(sha256(canonicalRequest)));

        var key = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
        key = hmac(key, region);
        key = hmac(key, "s3");
        key = hmac(key, "aws4_request");
        var signature = HEX.formatHex(hmac(key, stringToSign));

        return URI.create(
                scheme + "://" + host + canonicalPath + "?" + canonicalQuery + "&X-Amz-Signature=" + signature);
    }

    /** RFC 3986 encoding as SigV4 requires; slashes are kept in paths. */
    static String encode(String value, boolean encodeSlash) {
        var out = new StringBuilder();
        for (var b : value.getBytes(StandardCharsets.UTF_8)) {
            var c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_'
                    || c == '.'
                    || c == '~'
                    || (c == '/' && !encodeSlash)) {
                out.append(c);
            } else {
                out.append('%').append(HEX.withUpperCase().toHexDigits(b));
            }
        }
        return out.toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.vaabhi.shared.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS Signature Version 4 header signing for S3-compatible storage. The backup job sends a file with one
 * signed PUT instead of pulling in the AWS SDK. The {@code host}, {@code x-amz-content-sha256} and
 * {@code x-amz-date} headers, every {@code x-amz-*} header given by the caller and {@code Content-MD5} are
 * signed.
 */
public final class SigV4Signer {

    static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    static final HexFormat HEX = HexFormat.of();
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";

    private SigV4Signer() {}

    /**
     * Signs {@code method} on {@code uri}. Returns the headers the caller must add to the request:
     * {@code Authorization}, {@code x-amz-date} and {@code x-amz-content-sha256}.
     */
    public static Map<String, String> sign(
            String method,
            URI uri,
            Map<String, String> headers,
            String payloadSha256Hex,
            String region,
            String service,
            String accessKey,
            String secretKey,
            Instant now) {
        var amzDate = AMZ_DATE.format(now);
        var date = amzDate.substring(0, 8);
        var scope = date + "/" + region + "/" + service + "/aws4_request";

        var signed = new TreeMap<String, String>();
        signed.put("host", hostOf(uri));
        signed.put("x-amz-content-sha256", payloadSha256Hex);
        signed.put("x-amz-date", amzDate);
        headers.forEach((name, value) -> {
            var lower = name.toLowerCase(Locale.ROOT);
            if ((lower.startsWith("x-amz-")
                            && !lower.equals("x-amz-date")
                            && !lower.equals("x-amz-content-sha256"))
                    || lower.equals("content-md5")) {
                signed.put(lower, normalize(value));
            }
        });
        var signedHeaders = String.join(";", signed.keySet());
        var canonicalHeaders = signed.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining("\n"))
                + "\n";

        var canonicalPath = encode(uri.getPath() == null ? "" : uri.getPath(), false);
        var canonicalRequest = String.join(
                "\n", method, canonicalPath, canonicalQuery(uri), canonicalHeaders, signedHeaders, payloadSha256Hex);
        var stringToSign = String.join("\n", ALGORITHM, amzDate, scope, HEX.formatHex(sha256(canonicalRequest)));

        var key = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        var signature = HEX.formatHex(hmac(key, stringToSign));

        return Map.of(
                "Authorization",
                ALGORITHM + " Credential=" + accessKey + "/" + scope + ", SignedHeaders=" + signedHeaders
                        + ", Signature=" + signature,
                "x-amz-date",
                amzDate,
                "x-amz-content-sha256",
                payloadSha256Hex);
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

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] hmac(byte[] key, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hostOf(URI uri) {
        var authority = uri.getRawAuthority();
        if (authority == null) {
            throw new IllegalArgumentException("URI has no host: " + uri);
        }
        return authority.toLowerCase(Locale.ROOT);
    }

    private static String canonicalQuery(URI uri) {
        var raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return Arrays.stream(raw.split("&"))
                .filter(pair -> !pair.isEmpty())
                .map(pair -> {
                    var equals = pair.indexOf('=');
                    var name = equals < 0 ? pair : pair.substring(0, equals);
                    var value = equals < 0 ? "" : pair.substring(equals + 1);
                    return encode(name, true) + "=" + encode(value, true);
                })
                .sorted()
                .collect(Collectors.joining("&"));
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}

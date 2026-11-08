package com.vaabhi.tailoring;

import com.vaabhi.shared.storage.ObjectStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Photos taken at the counter: the customer, the cloth they brought, a garment brought for alteration and a
 * price-list product. The browser resizes to a 400 px WebP and PUTs it to {@code <key>/400.webp} with the
 * presigned URL; the key is then sent with the form.
 */
@Service
class TailoringImage {

    public enum Purpose {
        CLIENT("tailoring/clients/"),
        CLOTH("tailoring/cloth/"),
        ALTERATION("tailoring/alterations/"),
        PRODUCT("tailoring/products/");

        final String prefix;

        Purpose(String prefix) {
            this.prefix = prefix;
        }
    }

    static final int WIDTH = 400;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ObjectStorage storage;

    TailoringImage(ObjectStorage storage) {
        this.storage = storage;
    }

    TailoringImageUpload start(Purpose purpose) {
        if (!storage.configured()) {
            throw new ErrorResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Photo storage is not configured on this server"),
                    null);
        }
        var key = purpose.prefix + UUID.randomUUID();
        return new TailoringImageUpload(
                key,
                storage.presignPut(file(key), WINDOW).toString(),
                Instant.now().plus(WINDOW));
    }

    /** Rejects keys that were not issued for this purpose or whose file never arrived. Null (no photo) is fine. */
    void requireUploaded(Purpose purpose, @Nullable String key) {
        if (key == null) {
            return;
        }
        if (!key.matches("^" + purpose.prefix + "[0-9a-f-]{36}$") || !storage.exists(file(key))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload the photo again");
        }
    }

    private static String file(String key) {
        return key + "/" + WIDTH + ".webp";
    }
}

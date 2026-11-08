package com.vaabhi.settings;

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
 * Images that belong to the settings (shop logo, UPI QR). The browser resizes the image to a 400 px WebP and
 * uploads it to {@code <key>/400.webp} with the presigned URL; the key is then saved in the section.
 */
@Service
class SettingsImage {

    public enum Purpose {
        LOGO("settings/logo/"),
        UPI_QR("settings/upi-qr/");

        final String prefix;

        Purpose(String prefix) {
            this.prefix = prefix;
        }
    }

    static final int WIDTH = 400;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ObjectStorage storage;

    SettingsImage(ObjectStorage storage) {
        this.storage = storage;
    }

    SettingsImageUpload start(Purpose purpose) {
        if (!storage.configured()) {
            throw new ErrorResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Photo storage is not configured on this server"),
                    null);
        }
        var key = purpose.prefix + UUID.randomUUID();
        return new SettingsImageUpload(
                key,
                storage.presignPut(file(key), WINDOW).toString(),
                Instant.now().plus(WINDOW));
    }

    /** Rejects keys that were not issued for this purpose or whose file never arrived. Null (no image) is fine. */
    void requireUploaded(Purpose purpose, @Nullable String key) {
        if (key == null) {
            return;
        }
        if (!key.matches("^" + purpose.prefix + "[0-9a-f-]{36}$") || !storage.exists(file(key))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload the image again");
        }
    }

    private static String file(String key) {
        return key + "/" + WIDTH + ".webp";
    }
}

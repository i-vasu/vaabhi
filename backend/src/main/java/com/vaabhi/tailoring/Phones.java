package com.vaabhi.tailoring;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Phone numbers are stored as E.164 (+917013468353); a bare 10-digit number is taken as Indian. */
final class Phones {

    private Phones() {}

    static String normalize(String raw) {
        var digits = raw.replaceAll("[\\s\\-()]", "");
        if (digits.matches("^[6-9][0-9]{9}$")) {
            return "+91" + digits;
        }
        if (digits.matches("^0[6-9][0-9]{9}$")) {
            return "+91" + digits.substring(1);
        }
        if (digits.matches("^91[6-9][0-9]{9}$")) {
            return "+" + digits;
        }
        if (digits.matches("^\\+[1-9][0-9]{7,14}$")) {
            return digits;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid mobile number");
    }

    static @Nullable String normalizeOptional(@Nullable String raw) {
        return raw == null || raw.isBlank() ? null : normalize(raw);
    }
}

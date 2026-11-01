package com.vaabhi.customer;

import java.util.Optional;
import java.util.regex.Pattern;

/** Indian mobile numbers only (the shop ships within India; this also blocks premium-rate SMS abuse abroad). */
final class PhoneNumbers {

    private static final Pattern MOBILE = Pattern.compile("[6-9]\\d{9}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");

    private PhoneNumbers() {}

    /** Accepts 98765 43210, 098765-43210, 919876543210, +91 98765 43210; returns E.164 (+919876543210). */
    static Optional<String> normalize(String input) {
        var digits = SEPARATORS.matcher(input.strip()).replaceAll("");
        if (digits.startsWith("+91")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0091")) {
            digits = digits.substring(4);
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return MOBILE.matcher(digits).matches() ? Optional.of("+91" + digits) : Optional.empty();
    }

    /** +91******3210, for logs. */
    static String masked(String e164) {
        return e164.substring(0, 3) + "******" + e164.substring(e164.length() - 4);
    }
}

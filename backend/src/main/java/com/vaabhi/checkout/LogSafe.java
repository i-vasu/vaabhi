package com.vaabhi.checkout;

import org.jspecify.annotations.Nullable;

/** Values that came from a request or a payment gateway, made safe to log (no forged log lines). */
final class LogSafe {

    private LogSafe() {}

    /** The value with line breaks replaced, so it cannot start a new log line. */
    static String clean(@Nullable Object value) {
        return String.valueOf(value).replaceAll("[\n\r]", "_");
    }
}

package com.vaabhi.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * How one kind of document is numbered, e.g. prefix "ORD-", next 2042 → ORD-2042.
 *
 * @param nextValue the number the next document gets
 * @param pad zero-pads the number to this many digits (0 = none)
 * @param preview what the next document will be called (read-only)
 */
public record NumberSequence(
        @NotNull Kind kind,

        @NotNull @Size(max = 12) @Pattern(regexp = SAFE, message = SAFE_MESSAGE)
        String prefix,

        @Min(1) @Max(999_999_999) long nextValue,

        @NotNull @Size(max = 12) @Pattern(regexp = SAFE, message = SAFE_MESSAGE)
        String suffix,

        @Min(0) @Max(10) int pad,
        String preview) {

    static final String SAFE = "^[A-Za-z0-9/_\\-]*$";
    static final String SAFE_MESSAGE = "may use letters, digits, / - and _ only";

    public enum Kind {
        ORDER,
        INVOICE,
        ALTERATION,
        ITEM
    }

    public static String format(String prefix, long value, String suffix, int pad) {
        var number = pad > 0 ? String.format("%0" + pad + "d", value) : Long.toString(value);
        return prefix + number + suffix;
    }
}

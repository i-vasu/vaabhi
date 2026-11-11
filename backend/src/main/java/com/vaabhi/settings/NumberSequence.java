package com.vaabhi.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * How one kind of document is numbered, e.g. prefix "ORD-", next 2042 → ORD-2042. {FY} in the prefix or suffix
 * prints the financial year (April–March), e.g. "INV/{FY}/" → INV/26-27/0001.
 *
 * @param nextValue the number the next document gets
 * @param pad zero-pads the number to this many digits (0 = none)
 * @param reset FY: starts again at 1 every financial year (tax documents); NEVER: one running series
 * @param preview what the next document will be called (read-only)
 */
public record NumberSequence(
        @NotNull Kind kind,

        @NotNull @Size(max = 20) @Pattern(regexp = SAFE, message = SAFE_MESSAGE)
        String prefix,

        @Min(1) @Max(999_999_999) long nextValue,

        @NotNull @Size(max = 20) @Pattern(regexp = SAFE, message = SAFE_MESSAGE)
        String suffix,

        @Min(0) @Max(10) int pad,
        @Nullable Reset reset,
        String preview) {

    /** Letters, digits, / - _ and the {FY} token. */
    static final String SAFE = "^([A-Za-z0-9/_\\-]|\\{FY\\})*$";

    static final String SAFE_MESSAGE = "may use letters, digits, / - _ and {FY} only";

    public enum Kind {
        ORDER,
        INVOICE,
        ALTERATION,
        ITEM,
        WEB_ORDER,
        CREDIT_NOTE,
        STOCK_ITEM,
        PRODUCT
    }

    public enum Reset {
        NEVER,
        FY
    }

    /** The financial year a date falls in, as printed by {FY}: 1 Nov 2026 → "26-27", 1 Mar 2027 → "26-27". */
    public static String financialYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return String.format("%02d-%02d", start % 100, (start + 1) % 100);
    }

    public static String format(String prefix, long value, String suffix, int pad, String financialYear) {
        var number = pad > 0 ? String.format("%0" + pad + "d", value) : Long.toString(value);
        return (prefix + number + suffix).replace("{FY}", financialYear);
    }
}

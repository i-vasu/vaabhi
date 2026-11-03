package com.vaabhi.shared.rupees;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Helpers for rupee amounts: always two decimal places, rounded half up (as whole paise were before). */
public final class Rupees {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Rupees() {}

    /** The amount with exactly two decimal places. */
    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(long wholeRupees) {
        return BigDecimal.valueOf(wholeRupees).setScale(SCALE);
    }

    /** A rounded amount from a computed double (e.g. hours × an hourly rate). */
    public static BigDecimal of(double amount) {
        return of(BigDecimal.valueOf(amount));
    }

    /** Zero when null. */
    public static BigDecimal orZero(@Nullable BigDecimal amount) {
        return amount == null ? ZERO : of(amount);
    }

    /** {@code percent} % of {@code amount}, rounded to paise (10.5 means 10.5 %). */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return of(amount.multiply(percent).divide(HUNDRED, SCALE + 4, RoundingMode.HALF_UP));
    }

    /** {@code amount} × {@code factor}, rounded to paise. */
    public static BigDecimal times(BigDecimal amount, BigDecimal factor) {
        return of(amount.multiply(factor));
    }

    public static BigDecimal times(BigDecimal amount, long factor) {
        return of(amount.multiply(BigDecimal.valueOf(factor)));
    }

    /** {@code amount} ÷ {@code divisor}, rounded to paise. */
    public static BigDecimal divide(BigDecimal amount, BigDecimal divisor) {
        return amount.divide(divisor, SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount.signum() > 0;
    }

    public static <T> BigDecimal sum(Collection<T> items, Function<T, BigDecimal> amount) {
        return of(items.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}

package com.vaabhi.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

/** Pricing and stock rules shared by listing and product page. */
final class Stock {

    static final int LOW_STOCK_THRESHOLD = 3;

    private Stock() {}

    /** The exact count only when it is low; otherwise null, so competitors cannot read stock levels. */
    static @Nullable Integer stockLeft(long quantity) {
        return quantity > 0 && quantity <= LOW_STOCK_THRESHOLD ? (int) quantity : null;
    }

    /** Rounded percentage off MRP, matching the storefront's label. */
    static int discountPercent(BigDecimal priceRupees, BigDecimal mrpRupees) {
        if (mrpRupees.compareTo(priceRupees) <= 0) {
            return 0;
        }
        return mrpRupees
                .subtract(priceRupees)
                .multiply(BigDecimal.valueOf(100))
                .divide(mrpRupees, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}

package com.vaabhi.catalog;

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
    static int discountPercent(int pricePaise, int mrpPaise) {
        if (mrpPaise <= pricePaise) {
            return 0;
        }
        return (int) Math.round((mrpPaise - pricePaise) * 100.0 / mrpPaise);
    }
}

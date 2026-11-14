package com.vaabhi.money;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * An HSN/SAC code with its GST rate. A piece priced above the slab limit is taxed at the higher rate.
 *
 * @param slabLimitRupees price above which {@code rateAbovePercent} applies; null when there is no slab
 * @param inUse true when a category, garment template or price-list item uses it, or it is the default class
 */
public record TaxClass(
        long id,
        String name,
        Kind kind,
        String hsnCode,
        BigDecimal ratePercent,
        @Nullable BigDecimal slabLimitRupees,
        @Nullable BigDecimal rateAbovePercent,
        boolean inUse) {

    public enum Kind {
        GOODS,
        SERVICE
    }
}

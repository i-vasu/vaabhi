package com.vaabhi.accounts;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** One TDS section with the rates and thresholds the shop deducts at. */
public record TdsSectionView(
        long id,
        String code,
        String name,
        BigDecimal ratePercent,
        BigDecimal noPanRatePercent,
        @Nullable BigDecimal yearThresholdRupees,
        @Nullable BigDecimal singleThresholdRupees,
        boolean active) {}

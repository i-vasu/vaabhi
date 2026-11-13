package com.vaabhi.purchases;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** What the supplier's TDS section suggests for a bill, and a warning when the threshold is crossed without TDS. */
public record TdsSuggestion(
        @Nullable Long sectionId,
        @Nullable String sectionCode,
        BigDecimal ratePercent,
        BigDecimal suggestedRupees,
        boolean thresholdCrossed,
        @Nullable String warning) {}

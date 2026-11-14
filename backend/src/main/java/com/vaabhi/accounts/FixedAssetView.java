package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One asset with its accumulated depreciation and book value. */
public record FixedAssetView(
        long id,
        String name,
        long classId,
        String className,
        BigDecimal costRupees,
        LocalDate putToUseOn,
        BigDecimal openingAccdepRupees,
        BigDecimal accumulatedRupees,
        BigDecimal bookValueRupees,
        @Nullable Long billId,
        @Nullable String billNo,
        @Nullable LocalDate disposedOn,
        @Nullable BigDecimal disposalProceedsRupees,
        boolean hasPostedDepreciation) {}

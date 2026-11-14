package com.vaabhi.purchases;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** One line of a supplier bill as the admin sees it, with the names the ids stand for. */
public record SupplierBillLineView(
        long id,
        int position,
        String kind,
        String description,
        long accountId,
        String accountName,
        @Nullable Long stockItemId,
        @Nullable String stockItemName,
        @Nullable BigDecimal quantity,
        @Nullable Long assetClassId,
        @Nullable String assetClassName,
        @Nullable Long fixedAssetId,
        @Nullable String hsn,
        BigDecimal taxableRupees,
        BigDecimal gstRatePercent,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal igstRupees,
        BigDecimal totalRupees) {}

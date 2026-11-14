package com.vaabhi.tailoring;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A line of the stitching price list, e.g. "3 dart blouse" for ₹500 under the Blouse garment.
 *
 * @param stageCosts what each production stage costs the shop (piece rate paid to the tailor)
 * @param profitRupees price minus all stage costs
 * @param taxClassId the tax class this service is billed under; null = the garment's, then the shop default
 */
public record ServiceProduct(
        long id,
        long templateId,
        String garment,
        String code,
        String name,
        BigDecimal priceRupees,
        @Nullable String photoKey,
        List<StageCost> stageCosts,
        BigDecimal profitRupees,
        @Nullable Long taxClassId) {}

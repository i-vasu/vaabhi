package com.vaabhi.tailoring;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A line of the stitching price list, e.g. "3 dart blouse" for ₹500 under the Blouse garment.
 *
 * @param stageCosts what each production stage costs the shop (piece rate paid to the tailor)
 * @param profitPaise price minus all stage costs
 */
public record ServiceProduct(
        long id,
        long templateId,
        String garment,
        String code,
        String name,
        long pricePaise,
        @Nullable String photoKey,
        List<StageCost> stageCosts,
        long profitPaise) {}

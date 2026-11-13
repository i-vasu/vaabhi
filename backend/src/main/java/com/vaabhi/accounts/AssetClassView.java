package com.vaabhi.accounts;

import java.math.BigDecimal;

/** One asset class, its life and the two accounts its depreciation uses. */
public record AssetClassView(
        long id,
        String name,
        int lifeYears,
        BigDecimal residualPercent,
        long assetAccountId,
        String assetAccountCode,
        long accdepAccountId,
        String accdepAccountCode,
        boolean active) {}

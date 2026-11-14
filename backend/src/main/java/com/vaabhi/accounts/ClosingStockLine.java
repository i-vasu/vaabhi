package com.vaabhi.accounts;

import java.math.BigDecimal;

/** One material line of a year's closing stock list, with the item's on-hand quantity and value. */
public record ClosingStockLine(
        long stockItemId,
        String name,
        String unit,
        BigDecimal quantity,
        BigDecimal rateRupees,
        BigDecimal valueRupees) {}

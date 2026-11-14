package com.vaabhi.accounts;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** One line of the closing stock list being saved: the item's quantity and the rate to value it at. */
public record YearEndStockItemInput(
        long stockItemId,
        @NotNull @PositiveOrZero BigDecimal quantity,
        @NotNull @PositiveOrZero BigDecimal rateRupees) {}

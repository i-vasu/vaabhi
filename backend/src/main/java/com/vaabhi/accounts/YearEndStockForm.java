package com.vaabhi.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The closing stock list of a financial year: materials, finished goods and a note. */
public record YearEndStockForm(
        @NotNull @Valid List<YearEndStockItemInput> lines,
        @NotNull @PositiveOrZero BigDecimal finishedGoodsRupees,
        @Nullable @Size(max = 300) String note) {}

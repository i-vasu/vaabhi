package com.vaabhi.accounts;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** The column mapping and the optional opening and closing balances of a bank statement import. */
public record BankImportForm(
        ColumnMapping mapping,
        @Nullable BigDecimal openingRupees,
        @Nullable BigDecimal closingRupees) {}

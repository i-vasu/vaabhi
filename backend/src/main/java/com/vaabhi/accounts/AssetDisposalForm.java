package com.vaabhi.accounts;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Dispose of an asset, optionally for money received one way. */
public record AssetDisposalForm(
        @NotNull LocalDate disposedOn,
        @NotNull @DecimalMin("0") BigDecimal proceedsRupees,
        @Nullable String mode) {}

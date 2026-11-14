package com.vaabhi.purchases;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Asks what TDS the supplier's section suggests for a bill being typed, before it is saved. */
public record TdsCheckForm(
        @NotNull Long partyId,
        @NotNull LocalDate billDate,
        @NotNull BigDecimal taxableRupees,
        @Nullable Long billId) {}

package com.vaabhi.accounts;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The books start date, the payment mode to account map and the Shiprocket supplier. */
public record BooksSettingsForm(
        @Nullable LocalDate startDate,
        @NotNull Map<String, Long> modeAccounts,
        @Nullable Long shiprocketPartyId) {}

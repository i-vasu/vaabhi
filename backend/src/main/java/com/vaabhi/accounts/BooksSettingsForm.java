package com.vaabhi.accounts;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The books start date and which account each payment mode lands in. */
public record BooksSettingsForm(
        @Nullable LocalDate startDate, @NotNull Map<String, Long> modeAccounts) {}

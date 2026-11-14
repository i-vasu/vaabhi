package com.vaabhi.accounts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Create or edit an account. System accounts only take a new name and Schedule III line. */
public record AccountForm(
        @NotBlank @Pattern(regexp = "[0-9]{4}", message = "Code must be four digits")
        String code,

        @NotBlank @Size(max = 80) String name,
        @NotNull AccountType type,
        @Nullable Long parentId,
        @Nullable Boolean active,
        @Nullable ScheduleLine scheduleLine) {}

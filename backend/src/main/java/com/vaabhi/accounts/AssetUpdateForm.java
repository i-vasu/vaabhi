package com.vaabhi.accounts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Edit an asset's name, class and put-to-use date. */
public record AssetUpdateForm(
        @NotBlank String name,
        @NotNull Long classId,
        @NotNull LocalDate putToUseOn) {}

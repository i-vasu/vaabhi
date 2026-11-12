package com.vaabhi.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** A manual journal entry typed in by the owner. */
public record ManualEntryForm(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 200) String narration,
        @Size(min = 2, message = "Add at least two lines") List<@Valid ManualEntryLineInput> lines) {}

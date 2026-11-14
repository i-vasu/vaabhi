package com.vaabhi.purchases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Why and when a bill is cancelled. */
public record BillCancelForm(
        @NotNull LocalDate on, @NotBlank @Size(max = 200) String reason) {}

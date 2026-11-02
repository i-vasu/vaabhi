package com.vaabhi.money;

import com.vaabhi.tailoring.PaymentMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Record or edit an expense. */
public record ExpenseForm(
        @NotNull LocalDate spentOn,
        @NotNull ExpenseCategory category,
        @NotBlank @Size(max = 200) String description,
        @Min(1) @Max(100_000_000) long amountPaise,
        @NotNull PaymentMode mode,
        @Nullable @Size(max = 120) String paidTo) {}

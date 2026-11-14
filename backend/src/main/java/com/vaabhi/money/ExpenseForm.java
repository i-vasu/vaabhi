package com.vaabhi.money;

import com.vaabhi.tailoring.CounterMode;
import com.vaabhi.tailoring.PaymentMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Record or edit an expense. */
public record ExpenseForm(
        @NotNull LocalDate spentOn,
        @NotNull ExpenseCategory category,
        @NotBlank @Size(max = 200) String description,

        @DecimalMin("0.01") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal amountRupees,

        @NotNull @CounterMode PaymentMode mode,
        @Nullable @Size(max = 120) String paidTo) {}

package com.vaabhi.money;

import com.vaabhi.tailoring.PaymentMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** Money spent by the shop. */
public record Expense(
        long id,
        LocalDate spentOn,
        ExpenseCategory category,
        String description,
        long amountPaise,
        PaymentMode mode,
        @Nullable String paidTo,
        @Nullable String createdBy,
        OffsetDateTime createdAt) {}

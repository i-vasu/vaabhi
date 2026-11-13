package com.vaabhi.money;

import com.vaabhi.tailoring.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Money spent by the shop.
 *
 * @param fromPayroll recorded by paying a payslip; it is changed from Payroll, not edited here
 */
public record Expense(
        long id,
        LocalDate spentOn,
        ExpenseCategory category,
        String description,
        BigDecimal amountRupees,
        PaymentMode mode,
        @Nullable String paidTo,
        @Nullable String createdBy,
        OffsetDateTime createdAt,
        boolean fromPayroll) {}

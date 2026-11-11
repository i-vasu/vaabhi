package com.vaabhi.payroll;

import com.vaabhi.tailoring.CounterMode;
import com.vaabhi.tailoring.PaymentMode;
import jakarta.validation.constraints.NotNull;

/** Record that a payslip was paid, and how. */
public record PayForm(@NotNull @CounterMode PaymentMode mode) {}

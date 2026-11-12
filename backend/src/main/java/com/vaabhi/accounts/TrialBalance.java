package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Every account with a balance as at a date. {@code balanced} is false only if the books are broken. */
public record TrialBalance(
        LocalDate asOf,
        List<TrialBalanceLine> lines,
        BigDecimal totalDebitRupees,
        BigDecimal totalCreditRupees,
        boolean balanced) {}

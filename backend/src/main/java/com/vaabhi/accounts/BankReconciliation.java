package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** One statement's reconciliation: its closing against the ledger, what explains the difference, and clearing. */
public record BankReconciliation(
        BankStatementSummary statement,
        @Nullable BigDecimal statementClosingRupees,
        BigDecimal ledgerBalanceRupees,
        @Nullable BigDecimal differenceRupees,
        List<BankLineView> unmatchedLines,
        List<LedgerOnlyMovement> ledgerOnly,
        BigDecimal razorpayClearingRupees,
        BigDecimal codClearingRupees,
        @Nullable LocalDate oldestUnsettledWebPayment,
        boolean openingMismatch,
        List<BankLineView> ignoredLines) {}

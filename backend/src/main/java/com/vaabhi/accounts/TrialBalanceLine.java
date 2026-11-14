package com.vaabhi.accounts;

import java.math.BigDecimal;

/** An account's balance as at a date, in the debit or the credit column. */
public record TrialBalanceLine(
        long accountId, String code, String name, AccountType type, BigDecimal debitRupees, BigDecimal creditRupees) {}

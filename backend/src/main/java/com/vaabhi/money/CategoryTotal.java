package com.vaabhi.money;

import java.math.BigDecimal;

/** Spent on one category. */
public record CategoryTotal(ExpenseCategory category, BigDecimal amountRupees) {}

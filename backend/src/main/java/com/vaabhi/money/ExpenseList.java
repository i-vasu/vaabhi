package com.vaabhi.money;

import java.math.BigDecimal;
import java.util.List;

/** Expenses in a date range, newest first, with totals per category (largest first). */
public record ExpenseList(List<Expense> items, BigDecimal totalRupees, List<CategoryTotal> byCategory) {}

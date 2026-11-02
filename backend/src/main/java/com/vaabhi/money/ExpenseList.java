package com.vaabhi.money;

import java.util.List;

/** Expenses in a date range, newest first, with totals per category (largest first). */
public record ExpenseList(List<Expense> items, long totalPaise, List<CategoryTotal> byCategory) {}

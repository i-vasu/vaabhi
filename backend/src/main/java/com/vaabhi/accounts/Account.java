package com.vaabhi.accounts;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** One account in the chart. The balance is debits minus credits for assets and expenses, credits minus debits otherwise. */
public record Account(
        long id,
        String code,
        String name,
        AccountType type,
        @Nullable Long parentId,
        @Nullable String systemKey,
        boolean active,
        BigDecimal balanceRupees) {}

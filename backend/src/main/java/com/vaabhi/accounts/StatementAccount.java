package com.vaabhi.accounts;

import java.math.BigDecimal;

/** One account under a Schedule III line, with this period's amount and the previous period's. */
public record StatementAccount(
        long accountId, String code, String name, BigDecimal amountRupees, BigDecimal previousRupees) {}

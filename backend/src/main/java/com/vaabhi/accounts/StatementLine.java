package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.util.List;

/** A Schedule III line: its heading, the summed amount and the accounts shown under it. */
public record StatementLine(
        ScheduleLine line,
        String label,
        BigDecimal amountRupees,
        BigDecimal previousRupees,
        List<StatementAccount> accounts) {}

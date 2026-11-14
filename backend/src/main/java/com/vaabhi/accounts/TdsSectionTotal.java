package com.vaabhi.accounts;

import java.math.BigDecimal;

/** What was deducted under one TDS section in a quarter and what was deposited against it. */
public record TdsSectionTotal(
        String sectionCode, BigDecimal deductedRupees, BigDecimal depositedRupees, BigDecimal differenceRupees) {}

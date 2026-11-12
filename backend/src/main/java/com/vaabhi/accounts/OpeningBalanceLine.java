package com.vaabhi.accounts;

import java.math.BigDecimal;

/** What an account held on the books start date. */
public record OpeningBalanceLine(long accountId, BigDecimal debitRupees, BigDecimal creditRupees) {}

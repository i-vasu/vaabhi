package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** An account's postings between two dates, with the balance before them and after. */
public record LedgerStatement(
        long accountId,
        String accountCode,
        String accountName,
        AccountType type,
        LocalDate from,
        LocalDate to,
        BigDecimal openingRupees,
        BigDecimal totalDebitRupees,
        BigDecimal totalCreditRupees,
        BigDecimal closingRupees,
        List<LedgerStatementLine> lines) {}

package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One money movement read from a bank statement file; exactly one of debit and credit is above zero. */
public record ParsedBankLine(
        int rowNumber,
        LocalDate date,
        String description,
        @Nullable String reference,
        BigDecimal debit,
        BigDecimal credit,
        @Nullable BigDecimal balance) {}

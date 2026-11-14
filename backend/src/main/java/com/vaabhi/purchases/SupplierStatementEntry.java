package com.vaabhi.purchases;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line of a supplier's statement: a bill that owes money or a payment made. */
public record SupplierStatementEntry(
        LocalDate date,
        String kind /* BILL or PAYMENT */,
        long id,
        String reference,
        BigDecimal billedRupees,
        BigDecimal paidRupees,
        BigDecimal runningBalanceRupees) {}

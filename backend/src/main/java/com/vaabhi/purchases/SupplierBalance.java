package com.vaabhi.purchases;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One supplier and what the shop owes them. Billed and TDS come from their open bills, paid from every payment;
 * the balance is what is still payable, negative when they hold an advance. The oldest unpaid due date is the first
 * bill with money left on it.
 */
public record SupplierBalance(
        long partyId,
        String name,
        @Nullable String phone,
        @Nullable String gstin,
        @Nullable String state,
        BigDecimal billedRupees,
        BigDecimal paidRupees,
        BigDecimal tdsRupees,
        BigDecimal balanceRupees,
        @Nullable LocalDate oldestUnpaidDue,
        boolean overdue) {}

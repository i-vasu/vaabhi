package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A synced Razorpay settlement with the journal entry the sweeper posted for it. */
public record RazorpaySettlementView(
        String id,
        @Nullable String utr,
        LocalDate settledOn,
        BigDecimal grossRupees,
        BigDecimal feeRupees,
        BigDecimal taxRupees,
        BigDecimal netRupees,
        @Nullable Long journalEntryId,
        @Nullable String journalNumber) {}

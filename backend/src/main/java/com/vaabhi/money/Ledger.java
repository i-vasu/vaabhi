package com.vaabhi.money;

import java.time.LocalDate;
import java.util.List;

/**
 * Every payment, refund and expense in a date range, oldest first.
 *
 * @param openingPaise net of everything before {@code from}
 * @param inPaise payments received in the range
 * @param outPaise refunds and expenses in the range (positive)
 * @param closingPaise opening + in − out
 */
public record Ledger(
        LocalDate from,
        LocalDate to,
        long openingPaise,
        long inPaise,
        long outPaise,
        long closingPaise,
        List<ModeTotal> byMode,
        List<LedgerEntry> entries) {}

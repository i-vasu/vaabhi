package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Every payment, refund and expense in a date range, oldest first.
 *
 * @param openingRupees net of everything before {@code from}
 * @param inRupees payments received in the range
 * @param outRupees refunds and expenses in the range (positive)
 * @param closingRupees opening + in − out
 */
public record Ledger(
        LocalDate from,
        LocalDate to,
        BigDecimal openingRupees,
        BigDecimal inRupees,
        BigDecimal outRupees,
        BigDecimal closingRupees,
        List<ModeTotal> byMode,
        List<LedgerEntry> entries) {}

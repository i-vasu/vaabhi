package com.vaabhi.accounts;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Every active balance-sheet account (and any turned-off one the live entry still holds an amount on) with the
 * amounts of the live opening entry (zero when none).
 *
 * @param preStartOrders numbers of orders that took money before the start date and have a live invoice on or after it
 */
public record OpeningBalances(
        @Nullable LocalDate startDate,
        @Nullable Long entryId,
        @Nullable String entryNumber,
        List<OpeningBalanceLine> lines,
        List<String> preStartOrders) {}

package com.vaabhi.accounts;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Every active balance-sheet account with the amounts of the live opening entry (zero when none). */
public record OpeningBalances(
        @Nullable LocalDate startDate,
        @Nullable Long entryId,
        @Nullable String entryNumber,
        List<OpeningBalanceLine> lines) {}

package com.vaabhi.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** What each balance-sheet account held on the books start date. */
public record OpeningBalancesForm(@NotNull List<@Valid OpeningBalanceLine> lines) {}

package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One imported COD remittance with its AWB count and the journal entry the sweeper posted for it. */
public record CodRemittanceView(
        long id,
        String remittanceRef,
        @Nullable String utr,
        LocalDate remittedOn,
        BigDecimal codRupees,
        BigDecimal deductionRupees,
        BigDecimal netRupees,
        int awbCount,
        @Nullable String journalNumber) {}

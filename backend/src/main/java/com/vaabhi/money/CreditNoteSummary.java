package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A credit note in brief, as listed on its invoice. */
public record CreditNoteSummary(
        long id, String number, LocalDate issuedOn, BigDecimal totalRupees, CreditNote.Reason reason) {}

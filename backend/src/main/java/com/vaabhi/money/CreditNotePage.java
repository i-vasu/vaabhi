package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A page of the credit note register. */
public record CreditNotePage(List<Item> items, long total, int page, int size) {

    public record Item(
            long id,
            String number,
            long invoiceId,
            String invoiceNumber,
            String buyerName,
            LocalDate issuedOn,
            BigDecimal totalRupees,
            CreditNote.Reason reason) {}
}

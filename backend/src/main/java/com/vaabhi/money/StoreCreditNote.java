package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A credit note on a web order's invoice, as its own customer sees it. */
public record StoreCreditNote(
        String number,
        String title,
        CreditNote.Reason reason,
        LocalDate issuedOn,
        String invoiceNumber,
        String orderNumber,
        InvoiceParties.Seller seller,
        InvoiceParties.Buyer buyer,
        List<CreditNoteLine> lines,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal igstRupees,
        @Nullable String placeOfSupply,
        String supplyType,
        BigDecimal totalRupees) {}

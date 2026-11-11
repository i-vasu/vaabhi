package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A web order's invoice as its own customer sees it. */
public record StoreInvoice(
        String number,
        String title,
        LocalDate issuedOn,
        InvoiceParties.Seller seller,
        InvoiceParties.Buyer buyer,
        String orderNumber,
        List<InvoiceLine> lines,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal totalRupees,
        @Nullable String shopGstin,
        boolean cancelled) {}

package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A credit note: reverses an invoice, whole or in part, with the buyer and seller as the invoice carried them.
 *
 * @param invoiceId the invoice this reverses
 * @param orderNumber the shop or web order the invoice was for
 * @param title the document heading, always "Credit Note"
 * @param note the staff member's free text, if any
 */
public record CreditNote(
        long id,
        String number,
        long invoiceId,
        String invoiceNumber,
        String orderNumber,
        Reason reason,
        @Nullable String note,
        LocalDate issuedOn,
        String title,
        InvoiceParties.Seller seller,
        InvoiceParties.Buyer buyer,
        @Nullable String placeOfSupply,
        String supplyType,
        List<CreditNoteLine> lines,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal igstRupees,
        BigDecimal totalRupees,
        @Nullable String createdBy,
        OffsetDateTime createdAt) {

    public enum Reason {
        RETURN,
        CANCELLED,
        CORRECTION
    }
}

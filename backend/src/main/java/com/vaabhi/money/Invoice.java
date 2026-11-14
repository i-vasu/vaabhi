package com.vaabhi.money;

import com.vaabhi.tailoring.ClientSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A tax invoice for a shop or web order, with its amounts, buyer and seller as they were when it was issued.
 *
 * @param orderId the shop order's id; null for a web order
 * @param client the shop client; null for a web order
 * @param title the document heading, "Tax Invoice" or "Invoice"
 * @param discountRupees the lines minus the order total
 * @param orderTotalRupees what the order charged (items + delivery − discount)
 * @param taxableRupees value before GST
 * @param totalRupees what the customer pays in all (order total, plus GST when prices exclude it)
 * @param paidRupees what has been paid on the order so far (live)
 * @param creditNotes the credit notes issued against this invoice, oldest first
 */
public record Invoice(
        long id,
        String number,
        Source source,
        @Nullable Long orderId,
        String orderNumber,
        @Nullable ClientSummary client,
        String title,
        InvoiceParties.Seller seller,
        InvoiceParties.Buyer buyer,
        LocalDate issuedOn,
        List<InvoiceLine> lines,
        BigDecimal discountRupees,
        BigDecimal orderTotalRupees,
        BigDecimal gstRate,
        boolean pricesIncludeTax,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal igstRupees,
        @Nullable String placeOfSupply,
        String supplyType,
        BigDecimal totalRupees,
        BigDecimal paidRupees,
        BigDecimal balanceRupees,
        @Nullable String shopGstin,
        @Nullable String clientGstin,
        @Nullable String createdBy,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime cancelledAt,
        @Nullable String cancelledBy,
        List<CreditNoteSummary> creditNotes) {

    public enum Source {
        SHOP,
        WEB
    }
}

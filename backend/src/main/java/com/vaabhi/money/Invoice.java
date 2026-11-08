package com.vaabhi.money;

import com.vaabhi.tailoring.ClientSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A tax invoice for an order, with its amounts as they were when it was issued.
 *
 * @param discountRupees the lines minus the order total
 * @param orderTotalRupees what the order charged (items + delivery − discount)
 * @param taxableRupees value before GST
 * @param totalRupees what the customer pays in all (order total, plus GST when prices exclude it)
 * @param paidRupees what has been paid on the order so far (live)
 */
public record Invoice(
        long id,
        String number,
        long orderId,
        String orderNumber,
        ClientSummary client,
        LocalDate issuedOn,
        List<InvoiceLine> lines,
        BigDecimal discountRupees,
        BigDecimal orderTotalRupees,
        BigDecimal gstRate,
        boolean pricesIncludeTax,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal totalRupees,
        BigDecimal paidRupees,
        BigDecimal balanceRupees,
        @Nullable String shopGstin,
        @Nullable String clientGstin,
        @Nullable String createdBy,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime cancelledAt,
        @Nullable String cancelledBy) {}

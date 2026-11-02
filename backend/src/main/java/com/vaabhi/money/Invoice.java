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
 * @param discountPaise the lines minus the order total
 * @param orderTotalPaise what the order charged (items + delivery − discount)
 * @param taxablePaise value before GST
 * @param totalPaise what the customer pays in all (order total, plus GST when prices exclude it)
 * @param paidPaise what has been paid on the order so far (live)
 */
public record Invoice(
        long id,
        String number,
        long orderId,
        String orderNumber,
        ClientSummary client,
        LocalDate issuedOn,
        List<InvoiceLine> lines,
        long discountPaise,
        long orderTotalPaise,
        BigDecimal gstRate,
        boolean pricesIncludeTax,
        long taxablePaise,
        long cgstPaise,
        long sgstPaise,
        long totalPaise,
        long paidPaise,
        long balancePaise,
        @Nullable String shopGstin,
        @Nullable String clientGstin,
        @Nullable String createdBy,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime cancelledAt,
        @Nullable String cancelledBy) {}

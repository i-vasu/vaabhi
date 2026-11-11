package com.vaabhi.money;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What checkout hands over to invoice a web order: who bought, what, and the delivery charge. */
public record WebInvoiceRequest(
        long webOrderId,
        String orderNumber,
        @Nullable Long partyId,
        InvoiceParties.Buyer buyer,
        List<Line> lines,
        BigDecimal shippingRupees) {

    /** One item line; the amount is quantity times unit. */
    public record Line(String description, int quantity, BigDecimal unitRupees) {}
}

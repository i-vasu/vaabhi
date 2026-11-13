package com.vaabhi.purchases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A payment to a supplier, how it was spread over their bills and whether the books hold it. */
public record SupplierPaymentView(
        long id,
        long partyId,
        String supplierName,
        LocalDate paidOn,
        String mode,
        BigDecimal amountRupees,
        BigDecimal allocatedRupees,
        @Nullable String reference,
        @Nullable Long bankLineId,
        boolean posted,
        List<PaymentAllocationView> allocations) {

    /** One bill a payment went to. */
    public record PaymentAllocationView(long billId, String billNo, BigDecimal amountRupees) {}
}

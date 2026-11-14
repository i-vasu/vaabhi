package com.vaabhi.purchases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A supplier bill with its lines, what has been paid and whether the books hold it. */
public record SupplierBillView(
        long id,
        long partyId,
        String supplierName,
        String billNo,
        LocalDate billDate,
        @Nullable LocalDate dueDate,
        @Nullable String supplyStateCode,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal igstRupees,
        BigDecimal totalRupees,
        @Nullable Long tdsSectionId,
        BigDecimal tdsRupees,
        BigDecimal payableRupees,
        BigDecimal paidRupees,
        String source,
        String status,
        boolean posted,
        @Nullable String attachmentKey,
        @Nullable String notes,
        List<SupplierBillLineView> lines,
        @Nullable String tdsWarning) {}

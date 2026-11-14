package com.vaabhi.purchases;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One row of the bills list. */
public record SupplierBillListEntry(
        long id,
        String supplierName,
        String billNo,
        LocalDate billDate,
        @Nullable LocalDate dueDate,
        BigDecimal totalRupees,
        BigDecimal payableRupees,
        BigDecimal paidRupees,
        String status,
        String source,
        boolean posted) {}

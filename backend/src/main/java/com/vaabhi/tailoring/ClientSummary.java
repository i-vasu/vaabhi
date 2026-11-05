package com.vaabhi.tailoring;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One row of the customer list. */
public record ClientSummary(
        long id,
        String name,
        String phone,
        @Nullable String photoKey,
        ClientForm.ClientType clientType,
        int orderCount,
        BigDecimal spentRupees,
        BigDecimal outstandingRupees,
        @Nullable LocalDate lastOrderDate) {}

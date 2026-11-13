package com.vaabhi.purchases;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A supplier's details and the running account between the shop and them. */
public record SupplierStatement(
        SupplierBalance balance,
        @Nullable String email,
        @Nullable String pan,
        @Nullable String address,
        @Nullable String city,
        @Nullable String pinCode,
        @Nullable Long tdsSectionId,
        List<SupplierStatementEntry> entries) {}

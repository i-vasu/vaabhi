package com.vaabhi.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Turning an unmatched money-out line of a bank statement into a payment to a supplier. */
public record BankLinePaymentForm(
        @NotNull Long bankLineId,
        @NotNull Long partyId,
        @Nullable @Valid List<SupplierPaymentForm.PaymentAllocationInput> allocations) {}

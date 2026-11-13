package com.vaabhi.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What the admin types to pay a supplier, and optionally the bills the payment is spread over. */
public record SupplierPaymentForm(
        @NotNull Long partyId,
        @NotNull LocalDate paidOn,
        @NotNull String mode /* CASH | UPI | CARD | BANK */,
        @NotNull @DecimalMin("0.01") BigDecimal amountRupees,
        @Nullable @Size(max = 100) String reference,
        @Nullable @Size(max = 300) String notes,
        @Nullable @Valid List<PaymentAllocationInput> allocations) {

    /** How much of a payment goes to one bill; a null allocation list spreads it over the oldest open bills. */
    public record PaymentAllocationInput(
            @NotNull Long billId, @NotNull @DecimalMin("0.01") BigDecimal amountRupees) {}
}

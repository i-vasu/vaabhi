package com.vaabhi.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What the admin types for a supplier bill. */
public record SupplierBillForm(
        @NotNull Long partyId,
        @NotBlank @Size(max = 40) String billNo,
        @NotNull LocalDate billDate,
        @Nullable LocalDate dueDate,
        @Nullable @Size(min = 2, max = 2) String supplyStateCode,
        @Nullable Long tdsSectionId,
        @NotNull @DecimalMin("0") BigDecimal tdsRupees,
        @Nullable @Size(max = 300) String notes,
        @NotEmpty @Valid List<SupplierBillLineForm> lines,
        @Nullable PaidNow paidNow) {

    /** Pay the bill as soon as it is saved. */
    public record PaidNow(@NotNull String mode, @Nullable String reference) {}
}

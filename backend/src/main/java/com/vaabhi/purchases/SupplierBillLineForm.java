package com.vaabhi.purchases;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** One line of a supplier bill: an expense, stock received or a fixed asset bought. */
public record SupplierBillLineForm(
        @NotNull Kind kind,
        @NotBlank @Size(max = 200) String description,
        @Nullable Long accountId,
        @Nullable Long stockItemId,
        @Nullable BigDecimal quantity,
        @Nullable Long assetClassId,
        @Nullable @Size(max = 8) String hsn,
        @NotNull @DecimalMin("0.01") BigDecimal taxableRupees,
        @NotNull @DecimalMin("0") @DecimalMax("28") BigDecimal gstRatePercent) {

    public enum Kind {
        EXPENSE,
        STOCK,
        ASSET
    }
}

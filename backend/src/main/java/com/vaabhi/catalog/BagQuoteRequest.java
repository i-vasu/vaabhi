package com.vaabhi.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The shopper's bag as kept in the browser: what they chose, never what they were charged. */
public record BagQuoteRequest(@NotNull @Size(max = 20) List<@Valid @NotNull Line> lines) {

    @Schema(name = "BagRequestLine")
    public record Line(
            @NotBlank @Size(max = 64) String sku,
            @Nullable @Size(max = 40) String addonCode,
            @Min(1) @Max(BagQuoteService.MAX_QUANTITY) int quantity) {}
}

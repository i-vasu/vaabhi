package com.vaabhi.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The customer asks to return items of a delivered order: which lines (by position), how many, and why. */
public record ReturnRequestForm(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Size(min = 1, max = 50) List<@Valid @NotNull ReturnItem> items) {

    public record ReturnItem(
            @Min(0) int position, @Min(1) @Max(100) int quantity) {}
}

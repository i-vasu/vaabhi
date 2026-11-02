package com.vaabhi.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A delivery plan offered on a new order, e.g. "Express" in 3 days for ₹200.
 *
 * @param id null for a package added in this save
 * @param days the delivery date is the order date plus this many days
 */
public record DeliveryPackage(
        @Nullable Long id,
        @NotBlank @Size(max = 60) String name,
        @Min(0) @Max(100_000_000) long pricePaise,
        @Min(0) @Max(365) int days) {}

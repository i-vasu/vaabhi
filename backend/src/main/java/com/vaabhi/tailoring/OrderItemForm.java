package com.vaabhi.tailoring;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One garment on a new order.
 *
 * @param templateId garment (category); the measurements given are copied onto the order
 * @param serviceProductId price-list product, if any
 * @param description shown on the order, e.g. "3 dart blouse"
 */
public record OrderItemForm(
        @Nullable Long templateId,
        @Nullable Long serviceProductId,
        @NotBlank @Size(max = 120) String description,
        @Min(1) @Max(999) int quantity,

        @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal priceRupees,

        @Nullable @Size(max = 60) String colour,
        @Nullable @Size(max = 500) String note,
        @Nullable @Size(max = 60) Map<String, @Size(max = 40) String> measurements,
        @Nullable @Size(max = 200) String clothPhotoKey) {}

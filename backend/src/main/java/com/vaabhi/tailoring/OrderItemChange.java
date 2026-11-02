package com.vaabhi.tailoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One item when changing an order.
 *
 * @param id the existing item, or null for a new one (which starts at the first stage)
 */
public record OrderItemChange(
        @Nullable Long id,
        @Nullable Long templateId,
        @Nullable Long serviceProductId,
        @NotBlank @Size(max = 120) String description,
        @Min(1) @Max(999) int quantity,
        @Min(0) @Max(100_000_000) long pricePaise,
        @Nullable @Size(max = 60) String colour,
        @Nullable @Size(max = 500) String note,
        @Nullable @Size(max = 60) Map<String, @Size(max = 40) String> measurements,
        @Nullable @Size(max = 200) String clothPhotoKey) {

    OrderItemForm form() {
        return new OrderItemForm(
                templateId,
                serviceProductId,
                description,
                quantity,
                pricePaise,
                colour,
                note,
                measurements,
                clothPhotoKey);
    }
}

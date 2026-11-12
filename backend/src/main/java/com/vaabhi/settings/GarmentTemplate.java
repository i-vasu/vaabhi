package com.vaabhi.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The measurement form for one garment. Its fields appear on a new order and when adding a measurement.
 *
 * @param id null when creating
 * @param taxClassId the tax class garments of this kind are billed under; null = shop default
 */
public record GarmentTemplate(
        @Nullable Long id,
        @NotBlank @Size(max = 60) String name,
        @NotNull Audience businessType,
        @NotNull @Size(min = 1, max = 40) List<@Valid Field> fields,
        @Nullable Long taxClassId) {

    public enum Audience {
        ALL,
        LADIES,
        GENTS,
        KIDS
    }

    /**
     * @param key stable identifier stored with each measurement, e.g. {@code sleeve_length}
     * @param unit "in" or "cm", or empty for free text (e.g. neck style)
     */
    public record Field(
            @NotBlank
            @Size(max = 40)
            @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "use lowercase letters, digits and _")
            String key,

            @NotBlank @Size(max = 60) String label,

            @NotNull @Pattern(regexp = "^(in|cm|)$", message = "must be in, cm or empty")
            String unit) {}
}

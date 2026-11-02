package com.vaabhi.tailoring;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** New measurements for one garment; keys must be fields of the garment template. */
public record MeasurementForm(
        @NotNull Long templateId,
        @NotNull @Size(max = 60) Map<String, @Size(max = 40) String> values,
        @Nullable @Size(max = 500) String notes) {}

package com.vaabhi.tailoring;

import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Measurements taken for one garment, keyed by the garment template's field keys.
 *
 * @param garment the template's name at the time (for display)
 */
public record Measurement(
        long id,
        long templateId,
        String garment,
        Map<String, String> values,
        @Nullable String notes,
        @Nullable String takenBy,
        OffsetDateTime takenAt) {}

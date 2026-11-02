package com.vaabhi.tailoring;

import com.vaabhi.settings.ProductionStage;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One garment of an order and where it is in production.
 *
 * @param measurements the measurements copied at booking
 * @param assignedTo who is working on it in its current stage
 */
public record OrderItem(
        long id,
        @Nullable Long templateId,
        @Nullable Long serviceProductId,
        String description,
        int quantity,
        long pricePaise,
        @Nullable String colour,
        @Nullable String note,
        @Nullable Map<String, String> measurements,
        @Nullable String clothPhotoKey,
        @Nullable ProductionStage stage,
        OffsetDateTime stageChangedAt,
        @Nullable Assignee assignedTo) {}

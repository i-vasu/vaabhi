package com.vaabhi.tailoring;

import com.vaabhi.settings.ProductionStage;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One order item as the workshop sees it: what to make, for whom, by when, where it is and who has it.
 *
 * @param garment the garment template name, when the item has one
 * @param stageSince when the item reached its current stage
 * @param overdue the order is still open and its delivery date has passed
 */
public record WorkItem(
        long itemId,
        long orderId,
        String orderNumber,
        long clientId,
        String clientName,
        String description,
        int quantity,
        @Nullable String colour,
        @Nullable String garment,
        @Nullable String clothPhotoKey,
        ProductionStage stage,
        OffsetDateTime stageSince,
        LocalDate deliveryDate,
        @Nullable LocalDate trialDate,
        boolean dueToday,
        boolean overdue,
        @Nullable Assignee assignedTo) {}

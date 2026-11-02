package com.vaabhi.tailoring;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An alteration job.
 *
 * @param orderNumber the linked order's number, for display
 * @param undertaking the alteration wording the customer accepted, as printed on the slip
 * @param balancePaise total minus what was collected
 */
public record Alteration(
        long id,
        String number,
        ClientSummary client,
        @Nullable Long orderId,
        @Nullable String orderNumber,
        OrderStatus status,
        LocalDate readyBy,
        boolean paid,
        long totalPaise,
        long collectedPaise,
        long balancePaise,
        @Nullable String notes,
        String undertaking,
        @Nullable String createdBy,
        OffsetDateTime createdAt,
        @Nullable OffsetDateTime deliveredAt,
        List<AlterationItem> items) {}

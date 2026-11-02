package com.vaabhi.tailoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Move an alteration on. Alterations have no production stages: BOOKED → IN_PROGRESS → READY → DELIVERED, or
 * CANCELLED.
 *
 * @param collectPaise money taken now (usually on delivery); added to what was collected
 */
public record AlterationProgress(
        @NotNull OrderStatus status,
        @Min(0) @Max(100_000_000) long collectPaise) {}

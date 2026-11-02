package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Book an alteration.
 *
 * @param orderId the order the garments were stitched on, when the shop made them
 * @param paid false for a free alteration (e.g. a fit fix on the shop's own work); costs are then ignored
 * @param undertakingAccepted the customer agreed to the alteration wording printed on the slip
 */
public record AlterationForm(
        @NotNull Long clientId,
        @Nullable Long orderId,
        @NotNull LocalDate readyBy,
        boolean paid,
        @NotNull @Size(min = 1, max = 20) List<@Valid AlterationItemForm> items,
        @Nullable @Size(max = 1000) String notes,

        @AssertTrue(message = "the customer must accept the alteration terms")
        boolean undertakingAccepted) {}

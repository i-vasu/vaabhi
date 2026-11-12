package com.vaabhi.accounts;

import org.jspecify.annotations.Nullable;

/**
 * The document an entry came from: its kind (the entry's source type), its id and a readable label.
 *
 * @param orderId the shop order, for an order payment
 * @param alterationId the alteration, for an alteration payment
 * @param orderNumber the web order number, for a web order or a web return
 */
public record JournalSourceLink(
        String type,
        long id,
        String label,
        @Nullable Long orderId,
        @Nullable Long alterationId,
        @Nullable String orderNumber) {}

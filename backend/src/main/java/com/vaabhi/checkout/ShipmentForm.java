package com.vaabhi.checkout;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** How an order was sent; both optional (e.g. handed to a local delivery person). */
public record ShipmentForm(
        @Nullable @Size(max = 60) String courier,
        @Nullable @Size(max = 60) String trackingNumber) {}

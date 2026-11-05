package com.vaabhi.checkout;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Why the order is cancelled (shown to the shop; the customer gets an SMS). */
public record CancelWebOrderForm(@Nullable @Size(max = 200) String reason) {}

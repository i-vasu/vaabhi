package com.vaabhi.checkout;

import org.jspecify.annotations.Nullable;

/** What one Razorpay settlement sync did: whether the keys are set, how many rows were added, and any error. */
public record RazorpaySyncOutcome(
        boolean configured, int added, @Nullable String error) {}

package com.vaabhi.checkout;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** The shop's note when approving or rejecting a return (shown to the customer). */
public record ReturnDecisionForm(@Nullable @Size(max = 200) String note) {}

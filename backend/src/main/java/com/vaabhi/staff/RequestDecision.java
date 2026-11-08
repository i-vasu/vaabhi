package com.vaabhi.staff;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Approve or reject a pending request. */
public record RequestDecision(
        boolean approve, @Nullable @Size(max = 200) String note) {}

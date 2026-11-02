package com.vaabhi.tailoring;

import org.jspecify.annotations.Nullable;

/** Give an item to a staff member, or take it back with {@code staffId} null. */
public record AssignForm(@Nullable Long staffId) {}

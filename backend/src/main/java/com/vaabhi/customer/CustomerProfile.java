package com.vaabhi.customer;

import org.jspecify.annotations.Nullable;

/**
 * The signed-in customer. Phone sign-ups have no name until they add one; Google sign-ups have no phone.
 *
 * @param phone E.164, e.g. +919876543210
 */
public record CustomerProfile(
        long id,
        @Nullable String name,
        @Nullable String email,
        @Nullable String phone) {}

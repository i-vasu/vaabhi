package com.vaabhi.staff;

import org.jspecify.annotations.Nullable;

/**
 * A staff member as other modules see them.
 *
 * @param email their sign-in email; null for staff who do not use the dashboard
 */
public record StaffProfile(long id, String name, @Nullable String email, StaffRole role) {}

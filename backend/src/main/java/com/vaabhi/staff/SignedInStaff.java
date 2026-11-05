package com.vaabhi.staff;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The signed-in staff member, as shown in the dashboard header, with the areas their role may use now (the
 * dashboard hides the rest; the API checks the token's copy, which is refreshed at sign-in).
 */
public record SignedInStaff(
        long id, String name, @Nullable String email, StaffRole role, Set<Permission> permissions) {}

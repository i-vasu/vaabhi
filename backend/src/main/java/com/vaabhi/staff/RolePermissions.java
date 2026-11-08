package com.vaabhi.staff;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * What one role may do.
 *
 * @param editable false for the owner, who may always do everything
 */
public record RolePermissions(StaffRole role, @NotNull Set<Permission> permissions, boolean editable) {}

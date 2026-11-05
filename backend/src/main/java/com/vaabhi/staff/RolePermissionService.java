package com.vaabhi.staff;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** What managers and staff may do. Changes reach a person at their next sign-in. */
@Service
class RolePermissionService {

    private final JdbcClient jdbc;

    RolePermissionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    Set<Permission> permissions(StaffRole role) {
        if (role == StaffRole.OWNER) {
            return EnumSet.allOf(Permission.class);
        }
        var granted = jdbc
                .sql("SELECT permission FROM role_permission WHERE role = :role")
                .param("role", role.name())
                .query(String.class)
                .list()
                .stream()
                .map(Permission::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
        return granted.isEmpty() ? EnumSet.noneOf(Permission.class) : granted;
    }

    @Transactional(readOnly = true)
    List<RolePermissions> all() {
        return Arrays.stream(StaffRole.values())
                .map(role -> new RolePermissions(role, permissions(role), role != StaffRole.OWNER))
                .toList();
    }

    @Transactional
    RolePermissions save(StaffRole role, Set<Permission> permissions) {
        if (role == StaffRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The owner may always do everything");
        }
        jdbc.sql("DELETE FROM role_permission WHERE role = :role")
                .param("role", role.name())
                .update();
        for (var permission : permissions) {
            jdbc.sql("INSERT INTO role_permission (role, permission) VALUES (:role, :permission)")
                    .param("role", role.name())
                    .param("permission", permission.name())
                    .update();
        }
        return new RolePermissions(role, permissions(role), true);
    }
}

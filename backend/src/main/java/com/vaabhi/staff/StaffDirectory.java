package com.vaabhi.staff;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who works at the shop, for other modules (assigning work, payroll). Managing accounts stays in this module. */
@Service
public class StaffDirectory {

    private static final String SELECT = "SELECT id, name, email, role FROM staff_account";

    private final JdbcClient jdbc;

    StaffDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Active staff, by name. */
    @Transactional(readOnly = true)
    public List<StaffProfile> active() {
        return jdbc.sql(SELECT + " WHERE active ORDER BY lower(name), id")
                .query(StaffProfile.class)
                .list();
    }

    /** An active staff member. */
    @Transactional(readOnly = true)
    public Optional<StaffProfile> findActive(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id AND active")
                .param("id", id)
                .query(StaffProfile.class)
                .optional();
    }
}

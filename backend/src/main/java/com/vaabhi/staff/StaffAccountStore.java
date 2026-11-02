package com.vaabhi.staff;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** SQL for staff accounts. Every statement is static; all values are bind parameters. */
@Component
class StaffAccountStore {

    private static final String ID = "id";

    record Account(
            long id,
            String name,
            String email,
            StaffRole role,
            String passwordHash,
            boolean active,
            int failedLogins,
            @Nullable OffsetDateTime lockedUntil) {

        StaffProfile profile() {
            return new StaffProfile(id, name, email, role);
        }
    }

    private static final String SELECT = """
            SELECT id, name, email, role, password_hash, active, failed_logins, locked_until FROM staff_account
            """;

    private final JdbcClient jdbc;

    StaffAccountStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Account> findByEmail(String email) {
        return jdbc.sql(SELECT + " WHERE lower(email) = lower(:email)")
                .param("email", email.trim())
                .query(Account.class)
                .optional();
    }

    Optional<Account> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param(ID, id)
                .query(Account.class)
                .optional();
    }

    long count() {
        return jdbc.sql("SELECT COUNT(*) FROM staff_account").query(Long.class).single();
    }

    long create(String name, String email, @Nullable String phone, StaffRole role, String passwordHash) {
        return jdbc.sql("""
                        INSERT INTO staff_account (name, email, phone, role, password_hash)
                        VALUES (:name, :email, :phone, :role, :hash)
                        RETURNING id
                        """)
                .param("name", name)
                .param("email", email.trim())
                .param("phone", phone)
                .param("role", role.name())
                .param("hash", passwordHash)
                .query(Long.class)
                .single();
    }

    void setActive(long id, boolean active) {
        jdbc.sql("UPDATE staff_account SET active = :active, updated_at = now() WHERE id = :id")
                .param(ID, id)
                .param("active", active)
                .update();
    }

    void recordSuccess(long id, OffsetDateTime now) {
        jdbc.sql("""
                        UPDATE staff_account SET failed_logins = 0, locked_until = NULL, last_login_at = :now
                        WHERE id = :id
                        """).param(ID, id).param("now", now).update();
    }

    /** Counts a wrong password and locks the account once {@code maxFailures} is reached in a row. */
    void recordFailure(long id, int maxFailures, OffsetDateTime lockUntil) {
        jdbc.sql("""
                        UPDATE staff_account
                        SET failed_logins = CASE WHEN failed_logins + 1 >= :max THEN 0 ELSE failed_logins + 1 END,
                            locked_until = CASE WHEN failed_logins + 1 >= :max THEN :lockUntil ELSE locked_until END
                        WHERE id = :id
                        """)
                .param(ID, id)
                .param("max", maxFailures)
                .param("lockUntil", lockUntil)
                .update();
    }
}

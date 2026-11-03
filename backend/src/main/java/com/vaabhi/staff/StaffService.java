package com.vaabhi.staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adding and editing staff. Only the owner may make someone an owner or manager, or edit an owner; nobody can
 * switch themselves off, and the last active owner cannot be switched off or demoted.
 */
@Service
public class StaffService {

    private static final String ID = "id";
    private static final String SELECT = """
            SELECT id, name, phone, email, role, designation, joined_on, pay_type, salary_rupees, hourly_rupees,
                   shift_start, shift_end, late_grace_minutes, weekly_off, active,
                   password_hash IS NOT NULL AS can_sign_in, last_login_at
            FROM staff_account
            """;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    StaffService(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    private record Row(
            long id,
            String name,
            @Nullable String phone,
            @Nullable String email,
            StaffRole role,
            @Nullable String designation,
            @Nullable LocalDate joinedOn,
            PayType payType,
            BigDecimal salaryRupees,
            BigDecimal hourlyRupees,
            LocalTime shiftStart,
            LocalTime shiftEnd,
            int lateGraceMinutes,
            @Nullable Integer weeklyOff,
            boolean active,
            boolean canSignIn,
            @Nullable OffsetDateTime lastLoginAt) {

        StaffMember member() {
            return new StaffMember(
                    id,
                    name,
                    phone,
                    email,
                    role,
                    designation,
                    joinedOn,
                    payType,
                    salaryRupees,
                    hourlyRupees,
                    shiftStart,
                    shiftEnd,
                    lateGraceMinutes,
                    weeklyOff,
                    active,
                    canSignIn,
                    lastLoginAt);
        }
    }

    /** Everyone (active first, then by name), or only active staff. */
    @Transactional(readOnly = true)
    public List<StaffMember> list(boolean includeInactive) {
        return jdbc
                .sql(SELECT + " WHERE active OR :all ORDER BY active DESC, lower(name), id")
                .param("all", includeInactive)
                .query(Row.class)
                .list()
                .stream()
                .map(Row::member)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffMember get(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param(ID, id)
                .query(Row.class)
                .optional()
                .map(Row::member)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
    }

    @Transactional
    public StaffMember create(StaffForm form, boolean byOwner) {
        requireAllowed(form.role(), null, byOwner);
        validate(form, null);
        try {
            long id = jdbc.sql("""
                            INSERT INTO staff_account (name, phone, email, role, password_hash, designation, joined_on,
                                pay_type, salary_rupees, hourly_rupees, shift_start, shift_end, late_grace_minutes,
                                weekly_off)
                            VALUES (:name, :phone, :email, :role, :hash, :designation, :joinedOn, :payType, :salary,
                                :hourly, :shiftStart, :shiftEnd, :grace, :weeklyOff)
                            RETURNING id
                            """)
                    .params(params(form))
                    .param("hash", form.password() == null ? null : passwordEncoder.encode(form.password()))
                    .query(Long.class)
                    .single();
            return get(id);
        } catch (DuplicateKeyException e) {
            throw emailTaken(e);
        }
    }

    @Transactional
    public StaffMember update(long id, StaffForm form, boolean byOwner) {
        var before = get(id);
        requireAllowed(form.role(), before, byOwner);
        validate(form, before);
        if (before.role() == StaffRole.OWNER && form.role() != StaffRole.OWNER) {
            requireAnotherOwner(id);
        }
        try {
            var params = params(form);
            params.put(ID, id);
            jdbc.sql("""
                            UPDATE staff_account SET name = :name, phone = :phone, email = :email, role = :role,
                                designation = :designation, joined_on = :joinedOn, pay_type = :payType,
                                salary_rupees = :salary, hourly_rupees = :hourly, shift_start = :shiftStart,
                                shift_end = :shiftEnd, late_grace_minutes = :grace, weekly_off = :weeklyOff,
                                updated_at = now()
                            WHERE id = :id
                            """).params(params).update();
            if (form.password() != null) {
                jdbc.sql("""
                                UPDATE staff_account SET password_hash = :hash, failed_logins = 0, locked_until = NULL
                                WHERE id = :id
                                """)
                        .param("hash", passwordEncoder.encode(form.password()))
                        .param(ID, id)
                        .update();
            }
            return get(id);
        } catch (DuplicateKeyException e) {
            throw emailTaken(e);
        }
    }

    /** Takes away dashboard access; the person stays on the staff list. */
    @Transactional
    public StaffMember removeSignIn(long id, long callerId, boolean byOwner) {
        var member = get(id);
        requireAllowed(member.role(), member, byOwner);
        if (id == callerId) {
            throw bad("You cannot remove your own sign-in");
        }
        jdbc.sql("UPDATE staff_account SET password_hash = NULL, updated_at = now() WHERE id = :id")
                .param(ID, id)
                .update();
        return get(id);
    }

    /** Switches someone off (they leave the lists and cannot sign in) or back on. */
    @Transactional
    public StaffMember setActive(long id, boolean active, long callerId, boolean byOwner) {
        var member = get(id);
        requireAllowed(member.role(), member, byOwner);
        if (!active) {
            if (id == callerId) {
                throw bad("You cannot switch yourself off");
            }
            if (member.role() == StaffRole.OWNER) {
                requireAnotherOwner(id);
            }
        }
        jdbc.sql("UPDATE staff_account SET active = :active, updated_at = now() WHERE id = :id")
                .param("active", active)
                .param(ID, id)
                .update();
        return get(id);
    }

    // --- rules --------------------------------------------------------------------------------------------------

    private static void requireAllowed(StaffRole role, @Nullable StaffMember before, boolean byOwner) {
        if (byOwner) {
            return;
        }
        if (role != StaffRole.STAFF || (before != null && before.role() != StaffRole.STAFF)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can manage owners and managers");
        }
    }

    private static void validate(StaffForm form, @Nullable StaffMember before) {
        boolean hasEmail = form.email() != null && !form.email().isBlank();
        if (form.password() != null && !hasEmail) {
            throw bad("Add an email to sign in with");
        }
        if (!hasEmail && before != null && before.canSignIn()) {
            throw bad("Remove the sign-in before removing the email");
        }
        if (!form.shiftEnd().isAfter(form.shiftStart())) {
            throw bad("The shift must end after it starts");
        }
    }

    private void requireAnotherOwner(long id) {
        long others = jdbc.sql("SELECT COUNT(*) FROM staff_account WHERE role = 'OWNER' AND active AND id <> :id")
                .param(ID, id)
                .query(Long.class)
                .single();
        if (others == 0) {
            throw bad("The shop needs at least one owner");
        }
    }

    private static Map<String, Object> params(StaffForm form) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", form.name().trim());
        params.put("phone", blankToNull(form.phone()));
        params.put("email", blankToNull(form.email()));
        params.put("role", form.role().name());
        params.put("designation", blankToNull(form.designation()));
        params.put("joinedOn", form.joinedOn());
        params.put("payType", form.payType().name());
        params.put("salary", form.salaryRupees());
        params.put("hourly", form.hourlyRupees());
        params.put("shiftStart", form.shiftStart());
        params.put("shiftEnd", form.shiftEnd());
        params.put("grace", form.lateGraceMinutes());
        params.put("weeklyOff", form.weeklyOff());
        return params;
    }

    static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException emailTaken(Exception cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Another staff member signs in with this email", cause);
    }
}

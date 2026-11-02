package com.vaabhi.staff;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Leave, permission, off-day, comp-off and salary-advance requests. Approving leave, an off day or a comp-off marks
 * those days in attendance; an approved advance is taken back in the next payroll that is finalised.
 */
@Service
public class RequestService {

    /** Longest stretch one leave request may cover. */
    static final int MAX_DAYS = 31;

    private static final String SELECT = """
            SELECT r.id, r.staff_id, s.name AS staff_name, r.kind, r.from_date, r.to_date, r.hours, r.amount_paise,
                   r.reason, r.status, r.requested_by, r.decided_by, r.decided_at, r.decision_note, r.recovered_month,
                   r.created_at
            FROM staff_request r JOIN staff_account s ON s.id = r.staff_id
            """;

    private final JdbcClient jdbc;
    private final StaffService staff;
    private final AttendanceService attendance;

    RequestService(JdbcClient jdbc, StaffService staff, AttendanceService attendance) {
        this.jdbc = jdbc;
        this.staff = staff;
        this.attendance = attendance;
    }

    /** Pending first (oldest first), then decided ones (newest first). */
    @Transactional(readOnly = true)
    public List<StaffRequest> list(StaffRequest.@Nullable Status status, @Nullable Long staffId) {
        return jdbc.sql(SELECT + """
                        WHERE (CAST(:status AS varchar) IS NULL OR r.status = :status)
                          AND (CAST(:staff AS bigint) IS NULL OR r.staff_id = :staff)
                        ORDER BY r.status = 'PENDING' DESC,
                                 CASE WHEN r.status = 'PENDING' THEN r.created_at END,
                                 r.created_at DESC
                        LIMIT 200
                        """)
                .param("status", status == null ? null : status.name())
                .param("staff", staffId)
                .query(StaffRequest.class)
                .list();
    }

    @Transactional(readOnly = true)
    public StaffRequest get(long id) {
        return jdbc.sql(SELECT + " WHERE r.id = :id")
                .param("id", id)
                .query(StaffRequest.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    @Transactional
    public StaffRequest create(long staffId, RequestForm form, @Nullable String actor) {
        if (!staff.get(staffId).active()) {
            throw StaffService.bad("This person has been switched off");
        }
        var kind = form.kind();
        boolean span = kind == StaffRequest.Kind.LEAVE
                || kind == StaffRequest.Kind.OFF_DAY
                || kind == StaffRequest.Kind.COMP_OFF;
        LocalDate to = span && form.toDate() != null ? form.toDate() : form.fromDate();
        if (to.isBefore(form.fromDate())) {
            throw StaffService.bad("The last day is before the first");
        }
        if (ChronoUnit.DAYS.between(form.fromDate(), to) >= MAX_DAYS) {
            throw StaffService.bad("One request can cover at most " + MAX_DAYS + " days");
        }
        if (kind == StaffRequest.Kind.PERMISSION && form.hours() == null) {
            throw StaffService.bad("Say how many hours");
        }
        if (kind == StaffRequest.Kind.ADVANCE && form.amountPaise() == null) {
            throw StaffService.bad("Say how much advance");
        }
        long id = jdbc.sql("""
                        INSERT INTO staff_request (staff_id, kind, from_date, to_date, hours, amount_paise, reason,
                            requested_by)
                        VALUES (:staff, :kind, :from, :to, :hours, :amount, :reason, :by) RETURNING id
                        """)
                .param("staff", staffId)
                .param("kind", kind.name())
                .param("from", form.fromDate())
                .param("to", to)
                .param("hours", kind == StaffRequest.Kind.PERMISSION ? form.hours() : null)
                .param("amount", kind == StaffRequest.Kind.ADVANCE ? form.amountPaise() : null)
                .param("reason", StaffService.blankToNull(form.reason()))
                .param("by", actor)
                .query(Long.class)
                .single();
        return get(id);
    }

    /** Approved advances dated on or before {@code upTo} not yet taken back, per staff member. */
    @Transactional(readOnly = true)
    public Map<Long, Long> unrecoveredAdvances(LocalDate upTo) {
        return jdbc
                .sql("""
                        SELECT staff_id, SUM(amount_paise) AS total FROM staff_request
                        WHERE kind = 'ADVANCE' AND status = 'APPROVED' AND recovered_month IS NULL
                          AND from_date <= :upTo
                        GROUP BY staff_id
                        """)
                .param("upTo", upTo)
                .query((rs, n) -> Map.entry(rs.getLong("staff_id"), rs.getLong("total")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Marks those advances as taken back in the payroll {@code month} (its first day). */
    @Transactional
    public void markAdvancesRecovered(Collection<Long> staffIds, LocalDate upTo, LocalDate month) {
        if (staffIds.isEmpty()) {
            return;
        }
        jdbc.sql("""
                        UPDATE staff_request SET recovered_month = :month
                        WHERE kind = 'ADVANCE' AND status = 'APPROVED' AND recovered_month IS NULL
                          AND from_date <= :upTo AND staff_id = ANY(:staff)
                        """)
                .param("month", month)
                .param("upTo", upTo)
                .param("staff", staffIds.toArray(Long[]::new))
                .update();
    }

    /** Undoes {@link #markAdvancesRecovered} when a payroll month is reopened. */
    @Transactional
    public void releaseAdvances(LocalDate month) {
        jdbc.sql("UPDATE staff_request SET recovered_month = NULL WHERE recovered_month = :month")
                .param("month", month)
                .update();
    }

    @Transactional
    public StaffRequest decide(long id, RequestDecision decision, long callerId, @Nullable String actor) {
        var request = get(id);
        if (request.status() != StaffRequest.Status.PENDING) {
            throw StaffService.bad(
                    "This request was already " + request.status().name().toLowerCase());
        }
        if (request.staffId() == callerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Someone else has to decide your own request");
        }
        jdbc.sql("""
                        UPDATE staff_request SET status = :status, decided_by = :by, decided_at = now(),
                            decision_note = :note
                        WHERE id = :id
                        """)
                .param(
                        "status",
                        (decision.approve() ? StaffRequest.Status.APPROVED : StaffRequest.Status.REJECTED).name())
                .param("by", actor)
                .param("note", StaffService.blankToNull(decision.note()))
                .param("id", id)
                .update();
        if (decision.approve()) {
            var status = switch (request.kind()) {
                case LEAVE -> AttendanceStatus.LEAVE;
                case OFF_DAY -> AttendanceStatus.OFF;
                case COMP_OFF -> AttendanceStatus.COMP_OFF;
                default -> null;
            };
            if (status != null) {
                var note = "Approved " + request.kind().name().toLowerCase().replace('_', ' ')
                        + (request.reason() == null ? "" : ": " + request.reason());
                attendance.markFromRequest(
                        request.staffId(),
                        request.fromDate(),
                        request.toDate(),
                        status,
                        note.length() > 200 ? note.substring(0, 200) : note,
                        actor);
            }
        }
        return get(id);
    }
}

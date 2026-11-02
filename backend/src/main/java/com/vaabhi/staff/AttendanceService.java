package com.vaabhi.staff;

import com.vaabhi.settings.SettingsService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Marking who came in, late and overtime minutes from each person's shift, and the month view. */
@Service
public class AttendanceService {

    private final JdbcClient jdbc;
    private final StaffService staff;
    private final SettingsService settings;

    AttendanceService(JdbcClient jdbc, StaffService staff, SettingsService settings) {
        this.jdbc = jdbc;
        this.staff = staff;
        this.settings = settings;
    }

    /** Minutes after shift start beyond the grace period count in full; overtime is time after shift end. */
    static int lateMinutes(@Nullable LocalTime checkIn, LocalTime shiftStart, int graceMinutes) {
        if (checkIn == null) {
            return 0;
        }
        int late = (int) Duration.between(shiftStart, checkIn).toMinutes();
        return late > graceMinutes ? late : 0;
    }

    static int overtimeMinutes(@Nullable LocalTime checkOut, LocalTime shiftEnd) {
        return checkOut == null
                ? 0
                : (int) Math.max(0, Duration.between(shiftEnd, checkOut).toMinutes());
    }

    private record Record(
            long staffId,
            LocalDate day,
            AttendanceStatus status,
            @Nullable LocalTime checkIn,
            @Nullable LocalTime checkOut,
            int lateMinutes,
            int overtimeMinutes,
            @Nullable String note,
            @Nullable String markedBy) {}

    private List<Record> records(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT staff_id, day, status, check_in, check_out, late_minutes, overtime_minutes, note,
                               marked_by
                        FROM attendance WHERE day BETWEEN :from AND :to
                        """)
                .param("from", from)
                .param("to", to)
                .query(Record.class)
                .list();
    }

    // --- one day ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DayAttendance day(LocalDate day) {
        var calendar = settings.calendar();
        String holiday = calendar.holidays().stream()
                .filter(h -> h.date().equals(day))
                .map(h -> h.name())
                .findFirst()
                .orElse(calendar.weeklyOff().contains(day.getDayOfWeek()) ? "Weekly off" : null);
        Map<Long, Record> marked = new HashMap<>();
        records(day, day).forEach(r -> marked.put(r.staffId(), r));
        var entries = staff.list(false).stream()
                .map(member -> {
                    var r = marked.get(member.id());
                    AttendanceStatus suggested = null;
                    if (r == null) {
                        if (holiday != null) {
                            suggested = AttendanceStatus.HOLIDAY;
                        } else if (member.weeklyOff() != null
                                && member.weeklyOff() == day.getDayOfWeek().getValue()) {
                            suggested = AttendanceStatus.OFF;
                        }
                    }
                    return new AttendanceEntry(
                            member.id(),
                            member.name(),
                            member.designation(),
                            member.shiftStart(),
                            member.shiftEnd(),
                            r == null ? null : r.status(),
                            suggested,
                            r == null ? null : r.checkIn(),
                            r == null ? null : r.checkOut(),
                            r == null ? 0 : r.lateMinutes(),
                            r == null ? 0 : r.overtimeMinutes(),
                            r == null ? null : r.note(),
                            r == null ? null : r.markedBy());
                })
                .toList();
        return new DayAttendance(day, holiday, entries);
    }

    @Transactional
    public DayAttendance mark(long staffId, LocalDate day, AttendanceForm form, @Nullable String actor) {
        var member = staff.get(staffId);
        if (!member.active()) {
            throw StaffService.bad("This person has been switched off");
        }
        boolean worked = form.status().worked();
        var checkIn = worked ? form.checkIn() : null;
        var checkOut = worked ? form.checkOut() : null;
        if (checkIn != null && checkOut != null && !checkOut.isAfter(checkIn)) {
            throw StaffService.bad("Check-out must be after check-in");
        }
        upsert(
                staffId,
                day,
                form.status(),
                checkIn,
                checkOut,
                lateMinutes(checkIn, member.shiftStart(), member.lateGraceMinutes()),
                overtimeMinutes(checkOut, member.shiftEnd()),
                StaffService.blankToNull(form.note()),
                actor);
        return day(day);
    }

    @Transactional
    public DayAttendance markMany(LocalDate day, BulkAttendance bulk, @Nullable String actor) {
        for (var staffId : bulk.staffIds()) {
            var member = staff.get(staffId);
            if (member.active()) {
                upsert(staffId, day, bulk.status(), null, null, 0, 0, null, actor);
            }
        }
        return day(day);
    }

    @Transactional
    public DayAttendance clear(long staffId, LocalDate day) {
        jdbc.sql("DELETE FROM attendance WHERE staff_id = :staff AND day = :day")
                .param("staff", staffId)
                .param("day", day)
                .update();
        return day(day);
    }

    /** Marks days from an approved request; days already marked as worked are left alone. */
    void markFromRequest(
            long staffId, LocalDate from, LocalDate to, AttendanceStatus status, String note, @Nullable String actor) {
        for (var day = from; !day.isAfter(to); day = day.plusDays(1)) {
            jdbc.sql("""
                            INSERT INTO attendance (staff_id, day, status, note, marked_by)
                            VALUES (:staff, :day, :status, :note, :by)
                            ON CONFLICT (staff_id, day) DO UPDATE SET status = EXCLUDED.status, note = EXCLUDED.note,
                                marked_by = EXCLUDED.marked_by, check_in = NULL, check_out = NULL, late_minutes = 0,
                                overtime_minutes = 0, updated_at = now()
                            WHERE attendance.status NOT IN ('PRESENT', 'HALF_DAY')
                            """)
                    .param("staff", staffId)
                    .param("day", day)
                    .param("status", status.name())
                    .param("note", note)
                    .param("by", actor)
                    .update();
        }
    }

    private void upsert(
            long staffId,
            LocalDate day,
            AttendanceStatus status,
            @Nullable LocalTime checkIn,
            @Nullable LocalTime checkOut,
            int late,
            int overtime,
            @Nullable String note,
            @Nullable String actor) {
        jdbc.sql("""
                        INSERT INTO attendance (staff_id, day, status, check_in, check_out, late_minutes,
                            overtime_minutes, note, marked_by)
                        VALUES (:staff, :day, :status, :checkIn, :checkOut, :late, :overtime, :note, :by)
                        ON CONFLICT (staff_id, day) DO UPDATE SET status = EXCLUDED.status,
                            check_in = EXCLUDED.check_in, check_out = EXCLUDED.check_out,
                            late_minutes = EXCLUDED.late_minutes, overtime_minutes = EXCLUDED.overtime_minutes,
                            note = EXCLUDED.note, marked_by = EXCLUDED.marked_by, updated_at = now()
                        """)
                .param("staff", staffId)
                .param("day", day)
                .param("status", status.name())
                .param("checkIn", checkIn)
                .param("checkOut", checkOut)
                .param("late", late)
                .param("overtime", overtime)
                .param("note", note)
                .param("by", actor)
                .update();
    }

    // --- month --------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MonthAttendance month(YearMonth month) {
        var from = month.atDay(1);
        var to = month.atEndOfMonth();
        int length = month.lengthOfMonth();
        Map<Long, AttendanceStatus[]> days = new HashMap<>();
        Map<Long, int[]> minutes = new HashMap<>();
        for (var r : records(from, to)) {
            days.computeIfAbsent(r.staffId(), id -> new AttendanceStatus[length])[
                            r.day().getDayOfMonth() - 1] =
                    r.status();
            var m = minutes.computeIfAbsent(r.staffId(), id -> new int[2]);
            m[0] += r.lateMinutes();
            m[1] += r.overtimeMinutes();
        }
        var rows = new ArrayList<MonthAttendance.Row>();
        for (var member : staff.list(false)) {
            var marks = days.getOrDefault(member.id(), new AttendanceStatus[length]);
            int[] counts = new int[AttendanceStatus.values().length];
            int unmarked = 0;
            for (var status : marks) {
                if (status == null) {
                    unmarked++;
                } else {
                    counts[status.ordinal()]++;
                }
            }
            var m = minutes.getOrDefault(member.id(), new int[2]);
            rows.add(new MonthAttendance.Row(
                    member.id(),
                    member.name(),
                    Arrays.asList(marks),
                    counts[AttendanceStatus.PRESENT.ordinal()],
                    counts[AttendanceStatus.HALF_DAY.ordinal()],
                    counts[AttendanceStatus.ABSENT.ordinal()],
                    counts[AttendanceStatus.LEAVE.ordinal()],
                    counts[AttendanceStatus.OFF.ordinal()]
                            + counts[AttendanceStatus.HOLIDAY.ordinal()]
                            + counts[AttendanceStatus.COMP_OFF.ordinal()],
                    unmarked,
                    m[0],
                    m[1]));
        }
        return new MonthAttendance(from, to, rows);
    }

    static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the month as yyyy-mm, e.g. 2026-11", e);
        }
    }
}

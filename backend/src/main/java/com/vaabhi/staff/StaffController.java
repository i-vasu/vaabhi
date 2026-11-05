package com.vaabhi.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Staff, roles, attendance and requests. {@code /staff/**} needs the STAFF permission (changing a role's
 * permissions needs the owner); {@code /me/requests} is for everyone signed in (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin")
class StaffController {

    record PermissionsForm(@NotNull Set<Permission> permissions) {}

    private final StaffService staff;
    private final RolePermissionService roles;
    private final AttendanceService attendance;
    private final RequestService requests;

    StaffController(
            StaffService staff, RolePermissionService roles, AttendanceService attendance, RequestService requests) {
        this.staff = staff;
        this.roles = roles;
        this.attendance = attendance;
        this.requests = requests;
    }

    /** The caller's staff id, from the {@code staff:<id>} token subject. */
    static long callerId(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt && jwt.getSubject().startsWith(StaffAuthService.SUBJECT_PREFIX)) {
            try {
                return Long.parseLong(jwt.getSubject().substring(StaffAuthService.SUBJECT_PREFIX.length()));
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a staff sign-in", e);
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a staff sign-in");
    }

    static boolean isOwner(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));
    }

    static @Nullable String actor(Authentication auth) {
        return auth.getPrincipal() instanceof Jwt jwt ? jwt.getClaimAsString("name") : null;
    }

    // --- staff --------------------------------------------------------------------------------------------------

    @GetMapping("/staff")
    List<StaffMember> listStaff(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return staff.list(includeInactive);
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    StaffMember createStaff(@Valid @RequestBody StaffForm form, Authentication auth) {
        return staff.create(form, isOwner(auth));
    }

    @GetMapping("/staff/{id}")
    StaffMember getStaff(@PathVariable long id) {
        return staff.get(id);
    }

    @PutMapping("/staff/{id}")
    StaffMember updateStaff(@PathVariable long id, @Valid @RequestBody StaffForm form, Authentication auth) {
        return staff.update(id, form, isOwner(auth));
    }

    @PostMapping("/staff/{id}/deactivate")
    StaffMember deactivateStaff(@PathVariable long id, Authentication auth) {
        return staff.setActive(id, false, callerId(auth), isOwner(auth));
    }

    @PostMapping("/staff/{id}/activate")
    StaffMember activateStaff(@PathVariable long id, Authentication auth) {
        return staff.setActive(id, true, callerId(auth), isOwner(auth));
    }

    @DeleteMapping("/staff/{id}/sign-in")
    StaffMember removeStaffSignIn(@PathVariable long id, Authentication auth) {
        return staff.removeSignIn(id, callerId(auth), isOwner(auth));
    }

    // --- roles --------------------------------------------------------------------------------------------------

    @GetMapping("/staff/roles")
    List<RolePermissions> listRolePermissions() {
        return roles.all();
    }

    @PutMapping("/staff/roles/{role}")
    RolePermissions saveRolePermissions(@PathVariable StaffRole role, @Valid @RequestBody PermissionsForm form) {
        return roles.save(role, form.permissions());
    }

    // --- attendance ---------------------------------------------------------------------------------------------

    @GetMapping("/staff/attendance")
    DayAttendance getDayAttendance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return attendance.day(day);
    }

    @PutMapping("/staff/attendance/{day}/{staffId}")
    DayAttendance markAttendance(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @PathVariable long staffId,
            @Valid @RequestBody AttendanceForm form,
            Authentication auth) {
        return attendance.mark(staffId, day, form, actor(auth));
    }

    @DeleteMapping("/staff/attendance/{day}/{staffId}")
    DayAttendance clearAttendance(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day, @PathVariable long staffId) {
        return attendance.clear(staffId, day);
    }

    @PostMapping("/staff/attendance/{day}")
    DayAttendance markManyAttendance(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @Valid @RequestBody BulkAttendance bulk,
            Authentication auth) {
        return attendance.markMany(day, bulk, actor(auth));
    }

    /** {@code month} as yyyy-mm. */
    @GetMapping("/staff/attendance/month/{month}")
    MonthAttendance getMonthAttendance(@PathVariable String month) {
        return attendance.month(AttendanceService.parseMonth(month));
    }

    // --- requests -----------------------------------------------------------------------------------------------

    @GetMapping("/staff/requests")
    List<StaffRequest> listStaffRequests(
            @RequestParam(required = false) StaffRequest.@Nullable Status status,
            @RequestParam(required = false) @Nullable Long staffId) {
        return requests.list(status, staffId);
    }

    @PostMapping("/staff/requests")
    @ResponseStatus(HttpStatus.CREATED)
    StaffRequest createStaffRequest(@Valid @RequestBody RequestForm form, Authentication auth) {
        long staffId = form.staffId() != null ? form.staffId() : callerId(auth);
        return requests.create(staffId, form, actor(auth));
    }

    @PostMapping("/staff/requests/{id}/decision")
    StaffRequest decideStaffRequest(
            @PathVariable long id, @Valid @RequestBody RequestDecision decision, Authentication auth) {
        return requests.decide(id, decision, callerId(auth), actor(auth));
    }

    @GetMapping("/me/requests")
    List<StaffRequest> listOwnRequests(Authentication auth) {
        return requests.list(null, callerId(auth));
    }

    /** Anyone signed in may raise a request for themselves; {@code staffId} is ignored. */
    @PostMapping("/me/requests")
    @ResponseStatus(HttpStatus.CREATED)
    StaffRequest createOwnRequest(@Valid @RequestBody RequestForm form, Authentication auth) {
        return requests.create(callerId(auth), form, actor(auth));
    }
}

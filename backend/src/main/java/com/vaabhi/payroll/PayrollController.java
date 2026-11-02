package com.vaabhi.payroll;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Payslips, the production ledger and staff statements. Needs the PAYROLL permission (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/payroll")
class PayrollController {

    private final PayrollService payroll;
    private final Clock clock;

    PayrollController(PayrollService payroll, Clock clock) {
        this.payroll = payroll;
        this.clock = clock;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    private static YearMonth month(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the month as yyyy-mm, e.g. 2026-11", e);
        }
    }

    /** This month so far when no range is given. */
    @GetMapping("/production")
    ProductionLedger getProduction(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @RequestParam(required = false) @Nullable Long staffId) {
        var today = LocalDate.now(clock);
        return payroll.production(from != null ? from : today.withDayOfMonth(1), to != null ? to : today, staffId);
    }

    @GetMapping("/months/{month}")
    PayrollMonth getPayrollMonth(@PathVariable String month) {
        return payroll.month(month(month));
    }

    @PostMapping("/months/{month}/finalize")
    PayrollMonth finalizePayrollMonth(@PathVariable String month, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return payroll.finalizeMonth(month(month), actor(jwt));
    }

    @PostMapping("/months/{month}/reopen")
    PayrollMonth reopenPayrollMonth(@PathVariable String month) {
        return payroll.reopen(month(month));
    }

    @PostMapping("/payslips/{id}/pay")
    Payslip payPayslip(
            @PathVariable long id, @Valid @RequestBody PayForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return payroll.pay(id, form.mode(), actor(jwt));
    }

    @GetMapping("/staff/{staffId}/statement")
    StaffStatement getStaffStatement(@PathVariable long staffId) {
        return payroll.statement(staffId);
    }
}

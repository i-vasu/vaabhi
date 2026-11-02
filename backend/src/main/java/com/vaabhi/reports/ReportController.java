package com.vaabhi.reports;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The reports hub and reports. Needs the REPORTS permission (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/reports")
class ReportController {

    private final ReportService reports;
    private final Clock clock;

    ReportController(ReportService reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @GetMapping
    List<Report.ReportInfo> listReports() {
        return ReportService.CATALOGUE;
    }

    @GetMapping("/overview")
    Report.ReportsOverview getReportsOverview() {
        return reports.overview();
    }

    /** This month so far when no range is given. */
    @GetMapping("/{name}")
    Report getReport(
            @PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        var today = LocalDate.now(clock);
        return reports.report(name, from != null ? from : today.withDayOfMonth(1), to != null ? to : today);
    }
}

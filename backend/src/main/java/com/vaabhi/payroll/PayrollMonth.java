package com.vaabhi.payroll;

import java.time.LocalDate;
import java.util.List;

/**
 * A month's payroll: a preview worked out from attendance, production and advances until it is finalised, then the
 * frozen payslips.
 *
 * @param month first day of the month
 * @param unpaidPaise net still to pay on finalised payslips
 */
public record PayrollMonth(
        LocalDate month,
        boolean finalized,
        List<Payslip> slips,
        long totalNetPaise,
        long paidPaise,
        long unpaidPaise) {}

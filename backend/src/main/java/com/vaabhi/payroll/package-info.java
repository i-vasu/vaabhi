/**
 * Payroll: the production ledger (work stages each person finished, priced at the price list's piece rates),
 * monthly payslips (salary or daily wage less unpaid days, piece pay, overtime, lateness and advances), paying
 * them (which records a salary expense), and each person's statement. Reads the staff, tailoring and money modules;
 * nothing depends on this module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payroll")
package com.vaabhi.payroll;

/**
 * Money: invoices (raised automatically when an order is delivered, or by hand), what customers still owe,
 * expenses, and the financial ledger of every payment, refund and expense. Reads the tailoring module's orders and
 * payments; nothing depends on this module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Money")
package com.vaabhi.money;

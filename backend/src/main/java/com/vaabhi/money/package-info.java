/**
 * Money: invoices (raised automatically when a shop order is delivered, or by hand; web orders are invoiced by
 * checkout through {@link WebInvoiceService} when they ship or are delivered), what customers still owe, expenses,
 * and the financial ledger of every payment, refund and expense. Reads the tailoring module's orders and payments;
 * the checkout module depends on this module's web invoice API.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Money")
package com.vaabhi.money;

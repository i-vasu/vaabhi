/**
 * Reports: the overview (this month's figures, six months of revenue, dues ageing) and tabular reports over a date
 * range (sales, GST, orders, deliveries, customers, alterations, workshop, production, attendance, stock, rewards,
 * product profitability, ledger, expenses, usage), all in one shape the dashboard shows and exports as CSV. Reads
 * the other modules' tables; nothing depends on this module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reports")
package com.vaabhi.reports;

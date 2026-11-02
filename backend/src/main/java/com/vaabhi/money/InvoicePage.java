package com.vaabhi.money;

import java.util.List;

/**
 * A page of the invoice register.
 *
 * @param totalPaise sum of live (not cancelled) invoices matching the filters
 * @param taxPaise GST on those invoices
 */
public record InvoicePage(
        List<InvoiceSummary> items, long total, int page, int pageSize, long totalPaise, long taxPaise) {}

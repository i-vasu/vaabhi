package com.vaabhi.money;

import java.math.BigDecimal;
import java.util.List;

/**
 * A page of the invoice register.
 *
 * @param totalRupees sum of live (not cancelled) invoices matching the filters
 * @param taxRupees GST on those invoices
 * @param creditedRupees credit notes issued against the invoices matching the filters
 */
public record InvoicePage(
        List<InvoiceSummary> items,
        long total,
        int page,
        int pageSize,
        BigDecimal totalRupees,
        BigDecimal taxRupees,
        BigDecimal creditedRupees) {}

package com.vaabhi.purchases;

import com.vaabhi.accounts.SkippedFileRow;
import java.math.BigDecimal;
import java.util.List;

/** What one Shiprocket freight import added, skipped as already billed, and could not read. */
public record ShiprocketInvoiceSummary(
        int invoicesCreated,
        int invoicesSkipped,
        List<String> skippedInvoiceNos,
        List<SkippedFileRow> badRows,
        BigDecimal totalRupees) {}

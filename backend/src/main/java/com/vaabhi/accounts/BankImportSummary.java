package com.vaabhi.accounts;

import java.util.List;

/** What one bank statement import added, dropped as duplicates, matched, and skipped. */
public record BankImportSummary(
        long statementId, int added, int duplicates, int autoMatched, List<SkippedFileRow> skipped) {}

package com.vaabhi.accounts;

import java.util.List;

/** What one COD remittance import added, skipped as already imported, and could not match to an order. */
public record CodImportSummary(int added, int duplicates, List<String> unknownAwbs, List<SkippedFileRow> skipped) {}

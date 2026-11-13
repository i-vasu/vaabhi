package com.vaabhi.accounts;

import java.util.List;

/** A statement and its lines. */
public record BankStatementDetail(BankStatementSummary statement, List<BankLineView> lines) {}

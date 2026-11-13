package com.vaabhi.accounts;

import java.util.List;

/** The result of parsing a bank or remittance file: the lines and the rows that were left out. */
public record ParsedBankFile(List<ParsedBankLine> lines, List<SkippedFileRow> skipped) {}

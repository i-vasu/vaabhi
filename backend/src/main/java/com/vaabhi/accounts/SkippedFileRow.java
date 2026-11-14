package com.vaabhi.accounts;

/** A file row that was not imported, and why. */
public record SkippedFileRow(int rowNumber, String reason) {}

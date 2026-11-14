package com.vaabhi.accounts;

import org.jspecify.annotations.Nullable;

/** One thing that must hold before a financial year can be closed. A passing check has no message. */
public record YearEndCheck(String key, String label, boolean ok, @Nullable String message) {}

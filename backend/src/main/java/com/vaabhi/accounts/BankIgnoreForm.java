package com.vaabhi.accounts;

import org.jspecify.annotations.Nullable;

/** Why a bank line is being ignored. */
public record BankIgnoreForm(@Nullable String reason) {}

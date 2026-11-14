package com.vaabhi.accounts;

import org.jspecify.annotations.Nullable;

/** The account to post a bank line against, and an optional narration (the description is the default). */
public record BankPostForm(long accountId, @Nullable String narration) {}

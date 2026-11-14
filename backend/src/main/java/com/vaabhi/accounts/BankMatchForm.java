package com.vaabhi.accounts;

import org.jspecify.annotations.Nullable;

/** The entry to match a bank line to, and the account for any difference in the amounts. */
public record BankMatchForm(long journalEntryId, @Nullable Long differenceAccountId) {}

/**
 * Accounts: the general ledger. A chart of accounts, journal entries that can never be changed once posted (mistakes
 * are reversed), month locks, opening balances and the books start date. Other modules' documents become journal
 * entries through posting rules in this module that read their tables; no other module depends on this one. Uses
 * settings for journal numbers and reads party names. Reads money's GSTR-1 for GSTR-3B.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Accounts",
        allowedDependencies = {"settings", "party", "money", "shared :: rupees"})
package com.vaabhi.accounts;

/**
 * Purchases: suppliers, their bills and what is paid to them. Stock bought on a bill goes into inventory; the
 * books post bills and payments through accounts' posting rules, which read these tables. Nothing here writes to
 * the journal.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Purchases",
        allowedDependencies = {"settings", "party", "inventory", "accounts", "shared :: rupees", "shared :: storage"})
package com.vaabhi.purchases;

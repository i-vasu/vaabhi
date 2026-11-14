package com.vaabhi.accounts;

/** How a bank file writes the amount: either two columns (debit and credit) or one signed column. */
public enum AmountStyle {
    DEBIT_CREDIT,
    SIGNED
}

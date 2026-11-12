package com.vaabhi.accounts;

/** The five kinds of account. Assets and expenses grow with debits; the rest grow with credits. */
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    INCOME,
    EXPENSE;

    public boolean debitNormal() {
        return this == ASSET || this == EXPENSE;
    }

    public boolean onBalanceSheet() {
        return this == ASSET || this == LIABILITY || this == EQUITY;
    }
}

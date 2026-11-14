package com.vaabhi.accounts;

/** A Schedule III heading an account is shown under in the P&L or balance sheet. */
public enum ScheduleLine {
    REVENUE_FROM_OPERATIONS(AccountType.INCOME, "Revenue from operations"),
    OTHER_INCOME(AccountType.INCOME, "Other income"),
    COST_OF_MATERIALS(AccountType.EXPENSE, "Cost of materials consumed"),
    PURCHASES_STOCK_IN_TRADE(AccountType.EXPENSE, "Purchases of stock-in-trade"),
    CHANGE_IN_INVENTORIES(AccountType.EXPENSE, "Changes in inventories"),
    EMPLOYEE_BENEFITS(AccountType.EXPENSE, "Employee benefits expense"),
    FINANCE_COSTS(AccountType.EXPENSE, "Finance costs"),
    DEPRECIATION(AccountType.EXPENSE, "Depreciation and amortisation"),
    OTHER_EXPENSES(AccountType.EXPENSE, "Other expenses"),
    SHARE_CAPITAL(AccountType.EQUITY, "Share capital"),
    RESERVES_SURPLUS(AccountType.EQUITY, "Reserves and surplus"),
    LONG_TERM_BORROWINGS(AccountType.LIABILITY, "Long-term borrowings"),
    SHORT_TERM_BORROWINGS(AccountType.LIABILITY, "Short-term borrowings"),
    TRADE_PAYABLES(AccountType.LIABILITY, "Trade payables"),
    OTHER_CURRENT_LIABILITIES(AccountType.LIABILITY, "Other current liabilities"),
    PPE(AccountType.ASSET, "Property, plant and equipment"),
    INVENTORIES(AccountType.ASSET, "Inventories"),
    TRADE_RECEIVABLES(AccountType.ASSET, "Trade receivables"),
    CASH_AND_BANK(AccountType.ASSET, "Cash and cash equivalents"),
    OTHER_CURRENT_ASSETS(AccountType.ASSET, "Other current assets");

    private final AccountType side;
    private final String label;

    ScheduleLine(AccountType side, String label) {
        this.side = side;
        this.label = label;
    }

    public AccountType side() {
        return side;
    }

    public String label() {
        return label;
    }

    public boolean fits(AccountType type) {
        return side == type;
    }

    public static ScheduleLine defaultFor(AccountType type) {
        return switch (type) {
            case ASSET -> OTHER_CURRENT_ASSETS;
            case LIABILITY -> OTHER_CURRENT_LIABILITIES;
            case EQUITY -> RESERVES_SURPLUS;
            case INCOME -> OTHER_INCOME;
            case EXPENSE -> OTHER_EXPENSES;
        };
    }
}

package com.vaabhi.staff;

/**
 * A dashboard area a role may use. The owner may use every area; managers and staff get what their role allows
 * (Staff → Roles). Checked by SecurityConfig against the token's {@code permissions} claim.
 */
public enum Permission {
    /** Orders, customers, alterations, the price list and an order's invoice. */
    ORDERS,
    /** The work board and workshop overview. */
    WORKSHOP,
    /** Invoices, dues, expenses and the ledger. */
    MONEY,
    /** Staff, attendance and requests. */
    STAFF,
    /** Payslips and the production ledger. */
    PAYROLL,
    /** The online shop's products. */
    CATALOGUE,
    /** Changing shop settings (everyone may read them). */
    SETTINGS,
    /** Stock items, movements and the stock ledger. */
    STOCK,
    /** Coupons, rewards and the galleries. */
    MARKETING,
    /** Reports and the activity log. */
    REPORTS
}

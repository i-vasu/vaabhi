package com.vaabhi.staff;

/** How someone is paid. */
public enum PayType {
    /** A salary a month, less unpaid days. */
    MONTHLY,
    /** A wage for each day worked. */
    DAILY,
    /** By the production stages they finish, at the price list's piece rates. */
    PIECE
}

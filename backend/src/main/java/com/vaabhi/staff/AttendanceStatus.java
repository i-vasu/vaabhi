package com.vaabhi.staff;

/** A person's day. ABSENT, LEAVE and half of HALF_DAY are unpaid for monthly and daily pay. */
public enum AttendanceStatus {
    PRESENT,
    HALF_DAY,
    ABSENT,
    LEAVE,
    /** Their weekly off or an off day they asked for. */
    OFF,
    /** The shop was closed. */
    HOLIDAY,
    /** A day off in return for working on an off day. */
    COMP_OFF;

    /** Days they worked, for late and overtime minutes. */
    boolean worked() {
        return this == PRESENT || this == HALF_DAY;
    }
}

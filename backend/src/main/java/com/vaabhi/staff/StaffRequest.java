package com.vaabhi.staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A request from a staff member.
 *
 * @param hours PERMISSION: hours away during the day
 * @param amountRupees ADVANCE: amount asked for
 * @param recoveredMonth ADVANCE: first day of the payroll month it was taken back in
 */
public record StaffRequest(
        long id,
        long staffId,
        String staffName,
        Kind kind,
        LocalDate fromDate,
        LocalDate toDate,
        @Nullable BigDecimal hours,
        @Nullable BigDecimal amountRupees,
        @Nullable String reason,
        Status status,
        @Nullable String requestedBy,
        @Nullable String decidedBy,
        @Nullable OffsetDateTime decidedAt,
        @Nullable String decisionNote,
        @Nullable LocalDate recoveredMonth,
        OffsetDateTime createdAt) {

    public enum Kind {
        LEAVE,
        PERMISSION,
        OFF_DAY,
        COMP_OFF,
        ADVANCE
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }
}

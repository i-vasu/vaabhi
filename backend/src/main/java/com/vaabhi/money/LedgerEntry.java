package com.vaabhi.money;

import com.vaabhi.tailoring.PaymentMode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One movement of money.
 *
 * @param sourceId the order, alteration or expense it belongs to
 * @param reference order or alteration number, or what the expense was for
 * @param party the customer, or who an expense was paid to
 * @param amountRupees money in is positive, money out (refunds, expenses) negative
 * @param balanceRupees running balance after this entry, starting from the range's opening balance
 */
public record LedgerEntry(
        OffsetDateTime at,
        Kind kind,
        long sourceId,
        String reference,
        @Nullable String party,
        PaymentMode mode,
        BigDecimal amountRupees,
        BigDecimal balanceRupees) {
    public enum Kind {
        ORDER_PAYMENT,
        REFUND,
        ALTERATION_PAYMENT,
        EXPENSE,
        WEB_PAYMENT,
        WEB_REFUND
    }
}

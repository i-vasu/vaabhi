package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Money refunded for a returned web order item: Razorpay refunds go back through Razorpay, manual ones by bank. */
@Component
class WebReturnRefund implements PostingRule {

    private final RuleSupport support;

    WebReturnRefund(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            long orderId,
            String number,
            String refundMethod,
            BigDecimal refundRupees,
            OffsetDateTime refundedAt,
            @Nullable Long partyId) {}

    @Override
    public String sourceType() {
        return "WEB_RETURN";
    }

    @Override
    public String step() {
        return "REFUND";
    }

    @Override
    public int order() {
        return 62;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM web_order_return s WHERE s.refunded_at IS NOT NULL AND s.refund_rupees > 0"
                        + " AND ((s.refund_method = 'RAZORPAY' AND s.refund_status = 'PROCESSED')"
                        + " OR s.refund_method = 'MANUAL') AND " + RuleSupport.istDate("s.refunded_at")
                        + " >= :start AND " + RuleSupport.NOT_POSTED + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT r.id, r.order_id, r.number, r.refund_method, r.refund_rupees, r.refunded_at,
                               cu.party_id
                        FROM web_order_return r
                        JOIN web_order w ON w.id = r.order_id
                        JOIN customer cu ON cu.id = w.customer_id
                        WHERE r.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var held = support.webInvoiceLive(row.orderId(), row.refundedAt()) ? "DEBTORS" : "CUSTOMER_ADVANCES";
        var mode = "RAZORPAY".equals(row.refundMethod()) ? "RAZORPAY" : "BANK";
        return new NewEntry(
                RuleSupport.day(row.refundedAt()),
                sourceType(),
                sourceId,
                step(),
                "Refund on " + row.number(),
                RuleSupport.webKey(row.orderId()),
                null,
                List.of(
                        RuleSupport.debit(support.account(held), row.partyId(), row.refundRupees()),
                        RuleSupport.credit(support.modeAccount(mode), null, row.refundRupees())));
    }
}

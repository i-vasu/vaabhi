package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** A web order refunded through Razorpay (cancelled before shipping): Dr advances or Debtors, Cr Razorpay clearing. */
@Component
class WebOrderRefund implements PostingRule {

    private final RuleSupport support;

    WebOrderRefund(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            String number,
            BigDecimal refundedRupees,
            OffsetDateTime refundedAt,
            @Nullable Long partyId) {}

    @Override
    public String sourceType() {
        return "WEB_ORDER";
    }

    @Override
    public String step() {
        return "REFUND";
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM web_order s WHERE s.refund_status = 'PROCESSED' AND s.refunded_rupees > 0"
                        + " AND s.refunded_at IS NOT NULL AND " + RuleSupport.istDate("s.refunded_at")
                        + " >= :start AND " + RuleSupport.NOT_POSTED + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT w.id, w.number, w.refunded_rupees, w.refunded_at, cu.party_id
                        FROM web_order w JOIN customer cu ON cu.id = w.customer_id
                        WHERE w.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var held = support.webInvoiceLive(row.id(), row.refundedAt()) ? "DEBTORS" : "CUSTOMER_ADVANCES";
        return new NewEntry(
                RuleSupport.day(row.refundedAt()),
                sourceType(),
                sourceId,
                step(),
                "Refund on " + row.number(),
                RuleSupport.webKey(row.id()),
                null,
                List.of(
                        RuleSupport.debit(support.account(held), row.partyId(), row.refundedRupees()),
                        RuleSupport.credit(support.modeAccount("RAZORPAY"), null, row.refundedRupees())));
    }
}

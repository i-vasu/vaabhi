package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** A web order paid (online, or cash on delivery collected): Dr Razorpay or COD clearing, Cr advances or Debtors. */
@Component
class WebPaymentReceipt implements PostingRule {

    private final RuleSupport support;

    WebPaymentReceipt(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            String number,
            String paymentMethod,
            BigDecimal totalRupees,
            OffsetDateTime paidAt,
            @Nullable Long partyId) {}

    @Override
    public String sourceType() {
        return "WEB_ORDER";
    }

    @Override
    public String step() {
        return "RECEIPT";
    }

    @Override
    public int order() {
        return 11;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM web_order s WHERE s.paid_at IS NOT NULL AND "
                        + RuleSupport.istDate("s.paid_at") + " >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT w.id, w.number, w.payment_method, w.total_rupees, w.paid_at, cu.party_id
                        FROM web_order w JOIN customer cu ON cu.id = w.customer_id
                        WHERE w.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var held = support.webInvoiceLive(row.id(), row.paidAt()) ? "DEBTORS" : "CUSTOMER_ADVANCES";
        return new NewEntry(
                RuleSupport.day(row.paidAt()),
                sourceType(),
                sourceId,
                step(),
                "Payment on " + row.number(),
                RuleSupport.webKey(row.id()),
                null,
                List.of(
                        RuleSupport.debit(
                                support.modeAccount(RuleSupport.webMode(row.paymentMethod())), null, row.totalRupees()),
                        RuleSupport.credit(support.account(held), row.partyId(), row.totalRupees())));
    }
}

package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Money given back on a shop order: Dr Debtors (invoiced) or Customer advances, Cr the mode's account. */
@Component
class ShopRefund implements PostingRule {

    private final RuleSupport support;

    ShopRefund(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            long orderId,
            String number,
            String mode,
            BigDecimal amountRupees,
            OffsetDateTime receivedAt,
            @Nullable Long partyId) {}

    @Override
    public String sourceType() {
        return "ORDER_PAYMENT";
    }

    @Override
    public String step() {
        return "REFUND";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM order_payment s WHERE s.amount_rupees < 0 AND "
                        + RuleSupport.istDate("s.received_at") + " >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT p.id, p.order_id, o.number, p.mode, p.amount_rupees, p.received_at, c.party_id
                        FROM order_payment p
                        JOIN shop_order o ON o.id = p.order_id
                        JOIN client c ON c.id = o.client_id
                        WHERE p.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var amount = row.amountRupees().negate();
        var held = support.shopInvoiceLive(row.orderId(), row.receivedAt()) ? "DEBTORS" : "CUSTOMER_ADVANCES";
        return new NewEntry(
                RuleSupport.day(row.receivedAt()),
                sourceType(),
                sourceId,
                step(),
                "Refund on " + row.number(),
                RuleSupport.shopKey(row.orderId()),
                null,
                List.of(
                        RuleSupport.debit(support.account(held), row.partyId(), amount),
                        RuleSupport.credit(support.modeAccount(row.mode()), null, amount)));
    }
}

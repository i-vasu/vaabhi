package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Moves an order's customer advances against its invoice: Dr Customer advances, Cr Debtors. */
@Component
class InvoiceApply implements PostingRule {

    private final RuleSupport support;

    InvoiceApply(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            String number,
            @Nullable Long orderId,
            @Nullable Long webOrderId,
            @Nullable Long partyId,
            LocalDate issuedOn,
            BigDecimal totalRupees) {}

    @Override
    public String sourceType() {
        return "INVOICE";
    }

    @Override
    public String step() {
        return "APPLY";
    }

    @Override
    public int order() {
        return 30;
    }

    /** Live, issued invoices whose order holds customer advances; an invoice with none gets no APPLY row. */
    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending("""
                        SELECT s.id FROM invoice s
                        WHERE s.issued_on >= :start AND s.cancelled_at IS NULL
                          AND EXISTS (SELECT 1 FROM journal_entry i
                                      WHERE i.source_type = 'INVOICE' AND i.source_id = s.id
                                        AND i.source_step = 'ISSUE')
                          AND %s
                          AND COALESCE((SELECT SUM(l.credit_rupees - l.debit_rupees)
                                        FROM journal_line l JOIN journal_entry e ON e.id = l.entry_id
                                        WHERE l.account_id = (SELECT id FROM account
                                                              WHERE system_key = 'CUSTOMER_ADVANCES')
                                          AND e.order_key = CASE WHEN s.order_id IS NOT NULL
                                                                 THEN 'SHOP:' || s.order_id
                                                                 ELSE 'WEB:' || s.web_order_id END), 0) > 0
                        ORDER BY s.id LIMIT :limit
                        """.formatted(RuleSupport.NOT_POSTED), sourceType(), step(), start, limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT id, number, order_id, web_order_id, party_id, issued_on, total_rupees
                        FROM invoice WHERE id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var orderKey = RuleSupport.orderKey(row.orderId(), row.webOrderId());
        var advances = support.account("CUSTOMER_ADVANCES");
        var balance = support.jdbc()
                .sql("""
                        SELECT COALESCE(SUM(l.credit_rupees - l.debit_rupees), 0)
                        FROM journal_line l JOIN journal_entry e ON e.id = l.entry_id
                        WHERE l.account_id = :account AND e.order_key = :key
                        """)
                .param("account", advances)
                .param("key", orderKey)
                .query(BigDecimal.class)
                .single();
        var amount = Rupees.min(balance, row.totalRupees());
        if (amount.signum() <= 0) {
            throw new IllegalStateException("No customer advances to apply to " + row.number());
        }
        return new NewEntry(
                row.issuedOn(),
                sourceType(),
                sourceId,
                step(),
                "Advance applied to " + row.number(),
                orderKey,
                null,
                List.of(
                        RuleSupport.debit(advances, row.partyId(), amount),
                        RuleSupport.credit(support.account("DEBTORS"), row.partyId(), amount)));
    }
}

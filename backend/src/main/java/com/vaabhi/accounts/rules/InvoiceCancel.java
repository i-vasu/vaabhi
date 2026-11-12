package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.NewEntryLine;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** An invoice cancelled: one entry reversing its ISSUE and, if posted, its APPLY. */
@Component
class InvoiceCancel implements PostingRule {

    private final RuleSupport support;

    InvoiceCancel(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            String number,
            @Nullable Long orderId,
            @Nullable Long webOrderId,
            OffsetDateTime cancelledAt) {}

    record LineRow(long accountId, @Nullable Long partyId, BigDecimal debitRupees, BigDecimal creditRupees) {}

    @Override
    public String sourceType() {
        return "INVOICE";
    }

    @Override
    public String step() {
        return "CANCEL";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                """
                        SELECT s.id FROM invoice s
                        WHERE s.cancelled_at IS NOT NULL AND %s >= :start
                          AND EXISTS (SELECT 1 FROM journal_entry i
                                      WHERE i.source_type = 'INVOICE' AND i.source_id = s.id
                                        AND i.source_step = 'ISSUE')
                          AND %s
                        ORDER BY s.id LIMIT :limit
                        """.formatted(RuleSupport.istDate("s.cancelled_at"), RuleSupport.NOT_POSTED),
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT id, number, order_id, web_order_id, cancelled_at FROM invoice WHERE id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var original = support.jdbc()
                .sql("""
                        SELECT l.account_id, l.party_id, l.debit_rupees, l.credit_rupees
                        FROM journal_line l JOIN journal_entry e ON e.id = l.entry_id
                        WHERE e.source_type = 'INVOICE' AND e.source_id = :id AND e.source_step IN ('ISSUE', 'APPLY')
                        ORDER BY e.id, l.position
                        """)
                .param("id", sourceId)
                .query(LineRow.class)
                .list();
        var reversed = original.stream()
                .map(l -> new NewEntryLine(l.accountId(), l.partyId(), l.creditRupees(), l.debitRupees(), null))
                .toList();
        return new NewEntry(
                RuleSupport.day(row.cancelledAt()),
                sourceType(),
                sourceId,
                step(),
                "Cancelled invoice " + row.number(),
                RuleSupport.orderKey(row.orderId(), row.webOrderId()),
                null,
                reversed);
    }
}

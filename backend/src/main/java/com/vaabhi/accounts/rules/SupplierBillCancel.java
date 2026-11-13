package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.NewEntryLine;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** A supplier bill cancelled after its ISSUE was posted: one entry mirroring the bill's lines with the sides swapped. */
@Component
class SupplierBillCancel implements PostingRule {

    private final RuleSupport support;

    SupplierBillCancel(RuleSupport support) {
        this.support = support;
    }

    record Row(long id, String billNo, LocalDate cancelledOn) {}

    record LineRow(long accountId, @Nullable Long partyId, BigDecimal debitRupees, BigDecimal creditRupees) {}

    @Override
    public String sourceType() {
        return "SUPPLIER_BILL";
    }

    @Override
    public String step() {
        return "CANCEL";
    }

    @Override
    public int order() {
        return 64;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                """
                        SELECT s.id FROM supplier_bill s
                        WHERE s.status = 'CANCELLED' AND s.cancelled_on >= :start
                          AND EXISTS (SELECT 1 FROM journal_entry i
                                      WHERE i.source_type = 'SUPPLIER_BILL' AND i.source_id = s.id
                                        AND i.source_step = 'ISSUE')
                          AND %s
                        ORDER BY s.id LIMIT :limit
                        """.formatted(RuleSupport.NOT_POSTED),
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("SELECT id, bill_no, cancelled_on FROM supplier_bill WHERE id = :id")
                .param("id", sourceId)
                .query(Row.class)
                .single();
        var original = support.jdbc().sql("""
                        SELECT l.account_id, l.party_id, l.debit_rupees, l.credit_rupees
                        FROM journal_line l JOIN journal_entry e ON e.id = l.entry_id
                        WHERE e.source_type = 'SUPPLIER_BILL' AND e.source_id = :id AND e.source_step = 'ISSUE'
                        ORDER BY l.position
                        """).param("id", sourceId).query(LineRow.class).list();
        var reversed = original.stream()
                .map(l -> new NewEntryLine(l.accountId(), l.partyId(), l.creditRupees(), l.debitRupees(), null))
                .toList();
        return new NewEntry(
                row.cancelledOn(),
                sourceType(),
                sourceId,
                step(),
                "Bill " + row.billNo() + " cancelled",
                null,
                null,
                reversed);
    }
}

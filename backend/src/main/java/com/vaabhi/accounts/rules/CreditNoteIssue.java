package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.NewEntryLine;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** A credit note: Dr Sales returns and the GST parts, Cr Debtors for the total. */
@Component
class CreditNoteIssue implements PostingRule {

    private final RuleSupport support;

    CreditNoteIssue(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            String number,
            @Nullable Long orderId,
            @Nullable Long webOrderId,
            @Nullable Long partyId,
            LocalDate issuedOn,
            BigDecimal totalRupees,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees) {}

    @Override
    public String sourceType() {
        return "CREDIT_NOTE";
    }

    @Override
    public String step() {
        return "ISSUE";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM credit_note s WHERE s.issued_on >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT n.id, n.number, i.order_id, i.web_order_id, i.party_id, n.issued_on, n.total_rupees,
                               n.taxable_rupees, n.cgst_rupees, n.sgst_rupees, n.igst_rupees
                        FROM credit_note n JOIN invoice i ON i.id = n.invoice_id
                        WHERE n.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var lines = new ArrayList<NewEntryLine>();
        RuleSupport.addIfPositive(
                lines,
                row.taxableRupees(),
                RuleSupport.debit(support.account("SALES_RETURNS"), row.partyId(), row.taxableRupees()));
        lines.addAll(support.gstLines(true, null, row.cgstRupees(), row.sgstRupees(), row.igstRupees()));
        lines.add(RuleSupport.credit(support.account("DEBTORS"), row.partyId(), row.totalRupees()));
        return new NewEntry(
                row.issuedOn(),
                sourceType(),
                sourceId,
                step(),
                "Credit note " + row.number(),
                RuleSupport.orderKey(row.orderId(), row.webOrderId()),
                null,
                lines);
    }
}

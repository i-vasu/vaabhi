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

/** An invoice issued: Dr Debtors for the total, Cr Sales for the taxable value and Cr the GST parts. */
@Component
class InvoiceIssue implements PostingRule {

    private final RuleSupport support;

    InvoiceIssue(RuleSupport support) {
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
        return "INVOICE";
    }

    @Override
    public String step() {
        return "ISSUE";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM invoice s WHERE s.issued_on >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT id, number, order_id, web_order_id, party_id, issued_on, total_rupees, taxable_rupees,
                               cgst_rupees, sgst_rupees, igst_rupees
                        FROM invoice WHERE id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var lines = new ArrayList<NewEntryLine>();
        lines.add(RuleSupport.debit(support.account("DEBTORS"), row.partyId(), row.totalRupees()));
        RuleSupport.addIfPositive(
                lines,
                row.taxableRupees(),
                RuleSupport.credit(support.account("SALES"), row.partyId(), row.taxableRupees()));
        lines.addAll(support.gstLines(false, null, row.cgstRupees(), row.sgstRupees(), row.igstRupees()));
        return new NewEntry(
                row.issuedOn(),
                sourceType(),
                sourceId,
                step(),
                "Invoice " + row.number(),
                RuleSupport.orderKey(row.orderId(), row.webOrderId()),
                null,
                lines);
    }
}

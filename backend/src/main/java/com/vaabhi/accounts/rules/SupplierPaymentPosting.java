package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A payment to a supplier: Dr Creditors for the supplier, Cr the mode's account. When the payment was made from a bank
 * line, the line becomes matched with this entry.
 */
@Component
class SupplierPaymentPosting implements PostingRule {

    private final RuleSupport support;

    SupplierPaymentPosting(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            long partyId,
            String supplierName,
            LocalDate paidOn,
            String mode,
            BigDecimal amountRupees,
            @Nullable String reference) {}

    @Override
    public String sourceType() {
        return "SUPPLIER_PAYMENT";
    }

    @Override
    public String step() {
        return "PAYMENT";
    }

    @Override
    public int order() {
        return 62;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM supplier_payment s WHERE s.paid_on >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT p.id, p.party_id, s.name AS supplier_name, p.paid_on, p.mode, p.amount_rupees,
                               p.reference
                        FROM supplier_payment p JOIN party s ON s.id = p.party_id
                        WHERE p.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var narration =
                "Payment to " + row.supplierName() + (row.reference() == null ? "" : " (" + row.reference() + ")");
        return new NewEntry(
                row.paidOn(),
                sourceType(),
                sourceId,
                step(),
                narration,
                null,
                null,
                List.of(
                        RuleSupport.debit(support.account("CREDITORS"), row.partyId(), row.amountRupees()),
                        RuleSupport.credit(support.modeAccount(row.mode()), null, row.amountRupees())));
    }

    /** Marks the payment's bank line matched with the new entry, leaving a line that is no longer unmatched alone. */
    @Override
    public void posted(long sourceId, long entryId) {
        support.jdbc().sql("""
                        UPDATE bank_line
                        SET status = 'MATCHED', journal_entry_id = :entry, matched_by = 'system', matched_at = now()
                        WHERE id = (SELECT bank_line_id FROM supplier_payment WHERE id = :payment)
                          AND status = 'UNMATCHED'
                        """)
                .param("entry", entryId)
                .param("payment", sourceId)
                .update();
    }
}

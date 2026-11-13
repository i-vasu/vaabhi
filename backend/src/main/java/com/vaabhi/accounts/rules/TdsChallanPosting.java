package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.NewEntryLine;
import com.vaabhi.accounts.PostingRule;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** A TDS challan paid: Dr TDS payable, Dr other expense for the interest, Cr the mode's account. */
@Component
class TdsChallanPosting implements PostingRule {

    private final RuleSupport support;

    TdsChallanPosting(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            LocalDate paidOn,
            String mode,
            BigDecimal amountRupees,
            BigDecimal interestRupees,
            String challanNo,
            String sectionCode) {}

    @Override
    public String sourceType() {
        return "TDS_CHALLAN";
    }

    @Override
    public String step() {
        return "PAYMENT";
    }

    @Override
    public int order() {
        return 66;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM tds_challan s WHERE s.paid_on >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT c.id, c.paid_on, c.mode, c.amount_rupees, c.interest_rupees, c.challan_no,
                               s.code AS section_code
                        FROM tds_challan c
                        JOIN tds_section s ON s.id = c.tds_section_id
                        WHERE c.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var total = Rupees.of(row.amountRupees().add(row.interestRupees()));
        var lines = new ArrayList<NewEntryLine>();
        lines.add(RuleSupport.debit(support.account("TDS_PAYABLE"), null, row.amountRupees()));
        RuleSupport.addIfPositive(
                lines,
                row.interestRupees(),
                RuleSupport.debit(support.account("EXPENSE_OTHER"), null, row.interestRupees()));
        lines.add(RuleSupport.credit(support.modeAccount(row.mode()), null, total));
        return new NewEntry(
                row.paidOn(),
                sourceType(),
                sourceId,
                step(),
                "TDS challan " + row.challanNo() + " (" + row.sectionCode() + ")",
                null,
                null,
                lines);
    }
}

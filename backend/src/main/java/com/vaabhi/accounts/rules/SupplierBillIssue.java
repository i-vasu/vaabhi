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

/**
 * A supplier bill: Dr each line's account for its taxable value, Dr Input GST for the tax, Cr Creditors for what is
 * payable and Cr TDS payable for what was withheld.
 */
@Component
class SupplierBillIssue implements PostingRule {

    private static final int MAX_NARRATION = 200;

    private final RuleSupport support;

    SupplierBillIssue(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            long partyId,
            String supplierName,
            String billNo,
            LocalDate billDate,
            BigDecimal payableRupees,
            BigDecimal tdsRupees) {}

    record AccountRow(long accountId, BigDecimal taxableRupees, BigDecimal taxRupees) {}

    @Override
    public String sourceType() {
        return "SUPPLIER_BILL";
    }

    @Override
    public String step() {
        return "ISSUE";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM supplier_bill s WHERE s.bill_date >= :start AND s.status = 'OPEN' AND "
                        + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT b.id, b.party_id, p.name AS supplier_name, b.bill_no, b.bill_date, b.payable_rupees,
                               b.tds_rupees
                        FROM supplier_bill b JOIN party p ON p.id = b.party_id
                        WHERE b.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var accounts = support.jdbc().sql("""
                        SELECT l.account_id, SUM(l.taxable_rupees) AS taxable_rupees,
                               SUM(l.cgst_rupees + l.sgst_rupees + l.igst_rupees) AS tax_rupees
                        FROM supplier_bill_line l
                        WHERE l.bill_id = :id
                        GROUP BY l.account_id
                        ORDER BY MIN(l.position)
                        """).param("id", sourceId).query(AccountRow.class).list();
        var party = row.partyId();
        var lines = new ArrayList<NewEntryLine>();
        for (var account : accounts) {
            lines.add(RuleSupport.debit(account.accountId(), party, account.taxableRupees()));
        }
        var inputGst = Rupees.sum(accounts, AccountRow::taxRupees);
        RuleSupport.addIfPositive(
                lines, inputGst, RuleSupport.debit(support.account("INPUT_GST"), party, inputGst));
        RuleSupport.addIfPositive(
                lines, row.payableRupees(), RuleSupport.credit(support.account("CREDITORS"), party, row.payableRupees()));
        RuleSupport.addIfPositive(
                lines, row.tdsRupees(), RuleSupport.credit(support.account("TDS_PAYABLE"), party, row.tdsRupees()));
        return new NewEntry(
                row.billDate(),
                sourceType(),
                sourceId,
                step(),
                narration(row.billNo(), row.supplierName()),
                null,
                null,
                lines);
    }

    private static String narration(String billNo, String supplierName) {
        var text = "Bill " + billNo + " – " + supplierName;
        return text.length() <= MAX_NARRATION ? text : text.substring(0, MAX_NARRATION);
    }
}

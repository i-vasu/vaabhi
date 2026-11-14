package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.NewEntryLine;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A Shiprocket COD remittance paid into the bank: Dr Bank net, Dr Creditors for the deduction Shiprocket kept (freight
 * it will bill under its supplier party), Cr COD clearing cod. A deduction without the Shiprocket supplier set is a
 * posting problem.
 */
@Component
class CodRemittanceRule implements PostingRule {

    private final RuleSupport support;

    CodRemittanceRule(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            String remittanceRef,
            LocalDate remittedOn,
            BigDecimal codRupees,
            BigDecimal deductionRupees,
            BigDecimal netRupees) {}

    @Override
    public String sourceType() {
        return "COD_REMITTANCE";
    }

    @Override
    public String step() {
        return "SETTLE";
    }

    @Override
    public int order() {
        return 41;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM cod_remittance s WHERE s.remitted_on >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT id, remittance_ref, remitted_on, cod_rupees, deduction_rupees, net_rupees
                        FROM cod_remittance WHERE id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var lines = new ArrayList<NewEntryLine>();
        RuleSupport.addIfPositive(
                lines, row.netRupees(), RuleSupport.debit(support.account("BANK"), null, row.netRupees()));
        if (row.deductionRupees().signum() > 0) {
            var shiprocketPartyId = support.shiprocketPartyId();
            if (shiprocketPartyId == null) {
                throw new IllegalStateException("Pick the Shiprocket supplier in Books settings");
            }
            lines.add(RuleSupport.debit(support.account("CREDITORS"), shiprocketPartyId, row.deductionRupees()));
        }
        RuleSupport.addIfPositive(
                lines, row.codRupees(), RuleSupport.credit(support.account("COD_CLEARING"), null, row.codRupees()));
        return new NewEntry(
                row.remittedOn(),
                sourceType(),
                sourceId,
                step(),
                "Shiprocket COD remittance " + row.remittanceRef(),
                null,
                null,
                lines);
    }
}

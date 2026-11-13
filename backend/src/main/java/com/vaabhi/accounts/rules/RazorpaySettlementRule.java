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

/** A Razorpay settlement paid into the bank: Dr Bank net, Dr gateway fees and GST, Cr Razorpay clearing gross. */
@Component
class RazorpaySettlementRule implements PostingRule {

    /** The journal's source_id is {@code razorpay_settlement.seq}, not its Razorpay id. */
    private static final String NOT_POSTED = """
            NOT EXISTS (SELECT 1 FROM journal_entry j
                        WHERE j.source_type = :type AND j.source_id = s.seq AND j.source_step = :step)
            """;

    private final RuleSupport support;

    RazorpaySettlementRule(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long seq,
            String id,
            @Nullable String utr,
            LocalDate settledOn,
            BigDecimal grossRupees,
            BigDecimal feeRupees,
            BigDecimal taxRupees,
            BigDecimal netRupees) {}

    @Override
    public String sourceType() {
        return "RAZORPAY_SETTLEMENT";
    }

    @Override
    public String step() {
        return "SETTLE";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.seq FROM razorpay_settlement s WHERE s.settled_on >= :start AND " + NOT_POSTED
                        + " ORDER BY s.seq LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row =
                support.jdbc().sql("""
                        SELECT seq, id, utr, settled_on, gross_rupees, fee_rupees, tax_rupees, net_rupees
                        FROM razorpay_settlement WHERE seq = :seq
                        """).param("seq", sourceId).query(Row.class).single();
        var lines = new ArrayList<NewEntryLine>();
        RuleSupport.addIfPositive(
                lines, row.netRupees(), RuleSupport.debit(support.account("BANK"), null, row.netRupees()));
        RuleSupport.addIfPositive(
                lines,
                row.feeRupees(),
                RuleSupport.debit(support.account("EXPENSE_GATEWAY_FEES"), null, row.feeRupees()));
        RuleSupport.addIfPositive(
                lines, row.taxRupees(), RuleSupport.debit(support.account("INPUT_GST"), null, row.taxRupees()));
        RuleSupport.addIfPositive(
                lines,
                row.grossRupees(),
                RuleSupport.credit(support.account("RAZORPAY_CLEARING"), null, row.grossRupees()));
        return new NewEntry(
                row.settledOn(),
                sourceType(),
                sourceId,
                step(),
                "Razorpay settlement " + (row.utr() == null ? row.id() : row.utr()),
                null,
                null,
                lines);
    }
}

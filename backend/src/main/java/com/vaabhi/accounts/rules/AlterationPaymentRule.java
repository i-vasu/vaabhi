package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Alteration money in (RECEIPT: Dr mode, Cr Alteration income) or out (REFUND: the reverse). */
abstract class AlterationPaymentRule implements PostingRule {

    private final RuleSupport support;
    private final boolean refund;

    AlterationPaymentRule(RuleSupport support, boolean refund) {
        this.support = support;
        this.refund = refund;
    }

    record Row(
            long id,
            String number,
            String mode,
            BigDecimal amountRupees,
            OffsetDateTime receivedAt,
            @Nullable Long partyId) {}

    @Override
    public String sourceType() {
        return "ALTERATION_PAYMENT";
    }

    @Override
    public String step() {
        return refund ? "REFUND" : "RECEIPT";
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM alteration_payment s WHERE s.amount_rupees " + (refund ? "<" : ">") + " 0 AND "
                        + RuleSupport.istDate("s.received_at") + " >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT p.id, a.number, p.mode, p.amount_rupees, p.received_at, c.party_id
                        FROM alteration_payment p
                        JOIN alteration a ON a.id = p.alteration_id
                        JOIN client c ON c.id = a.client_id
                        WHERE p.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var amount = row.amountRupees().abs();
        var cash = support.modeAccount(row.mode());
        var income = support.account("ALTERATION_INCOME");
        return new NewEntry(
                RuleSupport.day(row.receivedAt()),
                sourceType(),
                sourceId,
                step(),
                (refund ? "Refund on " : "Payment on ") + row.number(),
                null,
                null,
                refund
                        ? List.of(
                                RuleSupport.debit(income, row.partyId(), amount),
                                RuleSupport.credit(cash, null, amount))
                        : List.of(
                                RuleSupport.debit(cash, null, amount),
                                RuleSupport.credit(income, row.partyId(), amount)));
    }
}

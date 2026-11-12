package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** An expense: Dr its category's expense account, Cr the mode's account. */
@Component
class ExpensePosting implements PostingRule {

    private final RuleSupport support;

    ExpensePosting(RuleSupport support) {
        this.support = support;
    }

    record Row(long id, LocalDate spentOn, String category, String mode, BigDecimal amountRupees) {}

    @Override
    public String sourceType() {
        return "EXPENSE";
    }

    @Override
    public String step() {
        return "EXPENSE";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                "SELECT s.id FROM expense s WHERE s.spent_on >= :start AND " + RuleSupport.NOT_POSTED
                        + " ORDER BY s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT id, spent_on, category, mode, amount_rupees FROM expense WHERE id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var category = row.category().charAt(0) + row.category().substring(1).toLowerCase(Locale.ROOT);
        return new NewEntry(
                row.spentOn(),
                sourceType(),
                sourceId,
                step(),
                "Expense: " + category,
                null,
                null,
                List.of(
                        RuleSupport.debit(support.account("EXPENSE_" + row.category()), null, row.amountRupees()),
                        RuleSupport.credit(support.modeAccount(row.mode()), null, row.amountRupees())));
    }
}

package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.PostingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** A month of depreciation of one asset: Dr Depreciation, Cr the class's accumulated-depreciation account. */
@Component
class AssetDepreciationPosting implements PostingRule {

    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final int MAX_NARRATION = 200;

    private final RuleSupport support;

    AssetDepreciationPosting(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            String assetName,
            LocalDate month,
            @Nullable LocalDate disposedOn,
            BigDecimal amountRupees,
            long accdepAccountId) {}

    @Override
    public String sourceType() {
        return "ASSET_DEPRECIATION";
    }

    @Override
    public String step() {
        return "DEPRECIATION";
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                """
                        SELECT s.id FROM asset_depreciation s
                        WHERE (s.month + INTERVAL '1 month' - INTERVAL '1 day')::date >= :start AND\s""" + RuleSupport.NOT_POSTED + " ORDER BY s.month, s.id LIMIT :limit",
                sourceType(),
                step(),
                start,
                limit);
    }

    @Override
    public NewEntry build(long sourceId) {
        var row = support.jdbc().sql("""
                        SELECT d.id, a.name AS asset_name, d.month, a.disposed_on, d.amount_rupees,
                               c.accdep_account_id
                        FROM asset_depreciation d
                        JOIN fixed_asset a ON a.id = d.asset_id
                        JOIN asset_class c ON c.id = a.asset_class_id
                        WHERE d.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var month = YearMonth.from(row.month());
        var date = row.disposedOn() != null && YearMonth.from(row.disposedOn()).equals(month)
                ? row.disposedOn()
                : month.atEndOfMonth();
        var narration = "Depreciation " + row.month().format(MONTH_YEAR) + " – " + row.assetName();
        if (narration.length() > MAX_NARRATION) {
            narration = narration.substring(0, MAX_NARRATION);
        }
        return new NewEntry(
                date,
                sourceType(),
                sourceId,
                step(),
                narration,
                null,
                null,
                List.of(
                        RuleSupport.debit(support.account("DEPRECIATION"), null, row.amountRupees()),
                        RuleSupport.credit(row.accdepAccountId(), null, row.amountRupees())));
    }
}

package com.vaabhi.accounts.rules;

import com.vaabhi.accounts.NewEntry;
import com.vaabhi.accounts.NewEntryLine;
import com.vaabhi.accounts.PostingRule;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * An asset disposed of: Dr its accumulated depreciation and the money received, Dr any loss, Cr its cost and any gain.
 */
@Component
class AssetDisposalPosting implements PostingRule {

    private static final int MAX_NARRATION = 200;

    private final RuleSupport support;

    AssetDisposalPosting(RuleSupport support) {
        this.support = support;
    }

    record Row(
            long id,
            String name,
            LocalDate disposedOn,
            @Nullable BigDecimal proceedsRupees,
            @Nullable String disposalMode,
            BigDecimal costRupees,
            BigDecimal openingAccdepRupees,
            long assetAccountId,
            long accdepAccountId) {}

    @Override
    public String sourceType() {
        return "ASSET";
    }

    @Override
    public String step() {
        return "DISPOSAL";
    }

    @Override
    public int order() {
        return 85;
    }

    @Override
    public List<Long> pending(LocalDate start, int limit) {
        return support.pending(
                """
                        SELECT s.id FROM fixed_asset s
                        WHERE s.disposed_on >= :start
                          AND NOT EXISTS (SELECT 1 FROM asset_depreciation d
                                          WHERE d.asset_id = s.id
                                            AND NOT EXISTS (SELECT 1 FROM journal_entry j
                                                            WHERE j.source_type = 'ASSET_DEPRECIATION'
                                                              AND j.source_id = d.id))
                          AND """
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
                        SELECT a.id, a.name, a.disposed_on, a.disposal_proceeds_rupees AS proceeds_rupees,
                               a.disposal_mode, a.cost_rupees, a.opening_accdep_rupees, c.asset_account_id,
                               c.accdep_account_id
                        FROM fixed_asset a
                        JOIN asset_class c ON c.id = a.asset_class_id
                        WHERE a.id = :id
                        """).param("id", sourceId).query(Row.class).single();
        var posted = jdbcPosted(row.id());
        var accdep = Rupees.of(row.openingAccdepRupees().add(posted));
        var cost = Rupees.of(row.costRupees());
        var book = cost.subtract(accdep);
        var proceeds = Rupees.orZero(row.proceedsRupees());
        var lines = new ArrayList<NewEntryLine>();
        RuleSupport.addIfPositive(
                lines, accdep, RuleSupport.debit(row.accdepAccountId(), null, accdep));
        if (proceeds.signum() > 0) {
            if (row.disposalMode() == null) {
                throw new IllegalStateException("The disposal has proceeds but no payment mode");
            }
            lines.add(RuleSupport.debit(support.modeAccount(row.disposalMode()), null, proceeds));
        }
        RuleSupport.addIfPositive(
                lines,
                book.subtract(proceeds),
                RuleSupport.debit(support.account("ASSET_LOSS"), null, book.subtract(proceeds)));
        lines.add(RuleSupport.credit(row.assetAccountId(), null, cost));
        RuleSupport.addIfPositive(
                lines,
                proceeds.subtract(book),
                RuleSupport.credit(support.account("ASSET_GAIN"), null, proceeds.subtract(book)));
        var narration = "Disposal of " + row.name();
        if (narration.length() > MAX_NARRATION) {
            narration = narration.substring(0, MAX_NARRATION);
        }
        return new NewEntry(row.disposedOn(), sourceType(), sourceId, step(), narration, null, null, lines);
    }

    private BigDecimal jdbcPosted(long assetId) {
        return support.jdbc().sql("""
                        SELECT COALESCE(SUM(d.amount_rupees), 0) FROM asset_depreciation d
                        WHERE d.asset_id = :id AND EXISTS (SELECT 1 FROM journal_entry j
                                WHERE j.source_type = 'ASSET_DEPRECIATION' AND j.source_id = d.id)
                        """).param("id", assetId).query(BigDecimal.class).single();
    }
}

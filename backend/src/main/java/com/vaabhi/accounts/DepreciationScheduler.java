package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps each asset's future depreciation rows in step with its cost, life and dates. Months already posted are fixed;
 * only the unposted ones are rebuilt.
 */
@Component
public class DepreciationScheduler {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final String UNPOSTED = """
            NOT EXISTS (SELECT 1 FROM journal_entry j
                        WHERE j.source_type = 'ASSET_DEPRECIATION' AND j.source_id = d.id)
            """;

    private final JdbcClient jdbc;
    private final BooksSettingsService settings;

    DepreciationScheduler(JdbcClient jdbc, BooksSettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    private record AssetRow(
            long id,
            BigDecimal costRupees,
            BigDecimal residualPercent,
            int lifeYears,
            BigDecimal openingAccdepRupees,
            LocalDate putToUseOn,
            @Nullable LocalDate disposedOn) {}

    @Transactional
    public void refresh(long assetId) {
        var asset = jdbc.sql("""
                        SELECT f.id, f.cost_rupees, c.residual_percent, c.life_years, f.opening_accdep_rupees,
                               f.put_to_use_on, f.disposed_on
                        FROM fixed_asset f
                        JOIN asset_class c ON c.id = f.asset_class_id
                        WHERE f.id = :id
                        """)
                .param("id", assetId)
                .query(AssetRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
        var start = settings.get().startDate();
        if (start == null) {
            deleteUnposted(assetId);
            return;
        }
        var throughMonth = YearMonth.now(IST).minusMonths(1);
        // A disposed asset's last (part) month is due at once, so the disposal entry, which waits for every
        // scheduled month to post, always comes after it.
        if (asset.disposedOn() != null && YearMonth.from(asset.disposedOn()).isAfter(throughMonth)) {
            throughMonth = YearMonth.from(asset.disposedOn());
        }
        var schedule = DepreciationMath.schedule(
                asset.costRupees(),
                asset.residualPercent(),
                asset.lifeYears(),
                asset.openingAccdepRupees(),
                asset.putToUseOn(),
                start,
                throughMonth,
                asset.disposedOn());
        deleteUnposted(assetId);
        var posted = new HashSet<YearMonth>();
        for (var month :
                jdbc.sql("""
                        SELECT d.month FROM asset_depreciation d
                        WHERE d.asset_id = :id AND EXISTS (SELECT 1 FROM journal_entry j
                                WHERE j.source_type = 'ASSET_DEPRECIATION' AND j.source_id = d.id)
                        """).param("id", assetId).query(LocalDate.class).list()) {
            posted.add(YearMonth.from(month));
        }
        for (var charge : schedule) {
            if (posted.contains(charge.month())) {
                continue;
            }
            jdbc.sql("""
                            INSERT INTO asset_depreciation (asset_id, month, amount_rupees)
                            VALUES (:asset, :month, :amount)
                            """)
                    .param("asset", assetId)
                    .param("month", charge.month().atDay(1))
                    .param("amount", charge.amount())
                    .update();
        }
    }

    /** Runs early every morning for every asset. */
    @Scheduled(cron = "0 15 1 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void refreshAll() {
        for (long id : jdbc.sql("SELECT id FROM fixed_asset ORDER BY id")
                .query(Long.class)
                .list()) {
            refresh(id);
        }
    }

    private void deleteUnposted(long assetId) {
        jdbc.sql("DELETE FROM asset_depreciation d WHERE d.asset_id = :id AND " + UNPOSTED)
                .param("id", assetId)
                .update();
    }
}

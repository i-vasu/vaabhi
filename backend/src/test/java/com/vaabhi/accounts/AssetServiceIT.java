package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The fixed-asset register and its monthly depreciation, against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class AssetServiceIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private AssetService assets;

    @Autowired
    private DepreciationScheduler depreciation;

    private static LocalDate today() {
        return LocalDate.now(IST);
    }

    private static LocalDate firstOfMonth(int monthsAgo) {
        return today().withDayOfMonth(1).minusMonths(monthsAgo);
    }

    /** The fixture's restoreStart() puts the previous date back afterwards. */
    private void setBooksStart(LocalDate start) {
        jdbc.sql("UPDATE books_setting SET start_date = :start WHERE id = 1")
                .param("start", start)
                .update();
    }

    private long assetClass(String name) {
        return jdbc.sql("SELECT id FROM asset_class WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private long supplier() {
        return jdbc.sql("INSERT INTO party (name, is_supplier) VALUES (:name, TRUE) RETURNING id")
                .param("name", unique("Asset supplier "))
                .query(Long.class)
                .single();
    }

    /** A bill line of kind ASSET and the asset it created, as the bill service would leave them. */
    private long assetFromBill(long classId, String cost, LocalDate putToUse, LocalDate billDate) {
        long account = jdbc.sql("SELECT asset_account_id FROM asset_class WHERE id = :id")
                .param("id", classId)
                .query(Long.class)
                .single();
        long bill = jdbc.sql("""
                        INSERT INTO supplier_bill (party_id, bill_no, bill_date, taxable_rupees, tax_rupees,
                                                   total_rupees, payable_rupees)
                        VALUES (:party, :number, :date, CAST(:cost AS numeric), 0, CAST(:cost AS numeric),
                                CAST(:cost AS numeric))
                        RETURNING id
                        """)
                .param("party", supplier())
                .param("number", unique("BILL-"))
                .param("date", billDate)
                .param("cost", cost)
                .query(Long.class)
                .single();
        long line = jdbc.sql("""
                        INSERT INTO supplier_bill_line (bill_id, position, kind, description, account_id,
                                                        asset_class_id, taxable_rupees, gst_rate_percent, total_rupees)
                        VALUES (:bill, 1, 'ASSET', 'Test asset', :account, :class, CAST(:cost AS numeric), 0,
                                CAST(:cost AS numeric))
                        RETURNING id
                        """)
                .param("bill", bill)
                .param("account", account)
                .param("class", classId)
                .param("cost", cost)
                .query(Long.class)
                .single();
        return jdbc.sql("""
                        INSERT INTO fixed_asset (name, asset_class_id, cost_rupees, put_to_use_on, bill_line_id)
                        VALUES (:name, :class, CAST(:cost AS numeric), :putToUse, :line)
                        RETURNING id
                        """)
                .param("name", unique("Asset "))
                .param("class", classId)
                .param("cost", cost)
                .param("putToUse", putToUse)
                .param("line", line)
                .query(Long.class)
                .single();
    }

    private List<LocalDate> depreciationMonths(long assetId) {
        return jdbc.sql("SELECT month FROM asset_depreciation WHERE asset_id = :id ORDER BY month")
                .param("id", assetId)
                .query(LocalDate.class)
                .list();
    }

    @Test
    void billAssetGetsScheduleAfterStartDateSet() {
        var start = firstOfMonth(3);
        long assetId = assetFromBill(assetClass("Computers"), "120000", start, start.minusDays(1));

        jdbc.sql("UPDATE books_setting SET start_date = NULL WHERE id = 1").update();
        depreciation.refresh(assetId);
        assertThat(depreciationMonths(assetId)).isEmpty();

        setBooksStart(start);
        depreciation.refresh(assetId);
        assertThat(depreciationMonths(assetId)).containsExactly(start, start.plusMonths(1), start.plusMonths(2));
        assertThat(jdbc.sql("SELECT SUM(amount_rupees) FROM asset_depreciation WHERE asset_id = :id")
                        .param("id", assetId)
                        .query(BigDecimal.class)
                        .single())
                .isEqualByComparingTo("9500.01");
    }

    @Test
    void threeMonthsPostThenDisposalGain() {
        var start = firstOfMonth(3);
        setBooksStart(start);
        long classId = assetClass("Computers");
        long assetId = assetFromBill(classId, "120000", start, start.minusDays(1));
        depreciation.refresh(assetId);
        sweeper.sweepNow();

        var before = assets.register().stream()
                .filter(asset -> asset.id() == assetId)
                .findFirst()
                .orElseThrow();
        assertThat(before.hasPostedDepreciation()).isTrue();
        assertThat(before.accumulatedRupees()).isEqualByComparingTo("9500.01");
        assertThat(before.bookValueRupees()).isEqualByComparingTo("110499.99");

        var proceeds = before.bookValueRupees().add(new BigDecimal("500"));
        assets.dispose(assetId, new AssetDisposalForm(today(), proceeds, "BANK"));
        sweeper.sweepNow();

        var disposal = entry("ASSET", assetId, "DISPOSAL");
        long assetAccount = jdbc.sql("SELECT asset_account_id FROM asset_class WHERE id = :id")
                .param("id", classId)
                .query(Long.class)
                .single();
        long accdepAccount = jdbc.sql("SELECT accdep_account_id FROM asset_class WHERE id = :id")
                .param("id", classId)
                .query(Long.class)
                .single();
        // Disposal first charges depreciation up to the disposal month, so compare with the register afterwards.
        var after = assets.register().stream()
                .filter(asset -> asset.id() == assetId)
                .findFirst()
                .orElseThrow();
        var gain = proceeds.subtract(new BigDecimal("120000").subtract(after.accumulatedRupees()));
        assertThat(netInEntry(disposal.id(), assetAccount)).isEqualByComparingTo("-120000");
        assertThat(netInEntry(disposal.id(), accdepAccount)).isEqualByComparingTo(after.accumulatedRupees());
        assertThat(after.accumulatedRupees()).isGreaterThanOrEqualTo(before.accumulatedRupees());
        assertThat(netInEntry(disposal.id(), accountId("ASSET_GAIN"))).isEqualByComparingTo(gain.negate());
        assertThat(netInEntry(disposal.id(), settings.accountFor("BANK")))
                .isEqualByComparingTo(proceeds); // money in: a debit
    }

    @Test
    void disposalWaitsForDepreciationRows() {
        var start = firstOfMonth(3);
        setBooksStart(start);
        long assetId = assetFromBill(assetClass("Computers"), "120000", start, start.minusDays(1));
        jdbc.sql("""
                        INSERT INTO asset_depreciation (asset_id, month, amount_rupees)
                        VALUES (:asset, :month, 100.00)
                        """)
                .param("asset", assetId)
                .param("month", start.minusMonths(2))
                .update();
        jdbc.sql("""
                        UPDATE fixed_asset SET disposed_on = :on, disposal_proceeds_rupees = 0 WHERE id = :id
                        """).param("on", today()).param("id", assetId).update();

        sweeper.sweepNow();
        assertThat(entries("ASSET", assetId)).isEmpty();

        jdbc.sql("DELETE FROM asset_depreciation WHERE asset_id = :id")
                .param("id", assetId)
                .update();
        sweeper.sweepNow();
        assertThat(entries("ASSET", assetId)).hasSize(1);
    }

    @Test
    void openingAssetAfterStartIs400() {
        var start = firstOfMonth(0);
        setBooksStart(start);
        var form = new OpeningAssetForm(
                "Opening machine", assetClass("Computers"), new BigDecimal("50000"), start, BigDecimal.ZERO);

        assertThatThrownBy(() -> assets.createOpening(form, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Assets bought after the books start come from a bill");
    }

    @Test
    void editAfterPostedDepreciationIs409() {
        var start = firstOfMonth(3);
        setBooksStart(start);
        long classId = assetClass("Computers");
        long assetId = assetFromBill(classId, "120000", start, start.minusDays(1));
        depreciation.refresh(assetId);
        sweeper.sweepNow();

        long otherClass = assetClass("Office equipment");
        assertThatThrownBy(() -> assets.update(assetId, new AssetUpdateForm("Renamed", otherClass, start)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only the name can change");

        var renamed = assets.update(assetId, new AssetUpdateForm("Renamed", classId, start));
        assertThat(renamed.name()).isEqualTo("Renamed");
    }

    @Test
    void newClassCreatesTwoAccounts() {
        int highest = jdbc.sql("SELECT COALESCE(MAX(CAST(code AS int)), 0) FROM account WHERE code ~ '^15[0-9]0$'")
                .query(Integer.class)
                .single();
        int next = highest == 0 ? 1500 : highest + 10;
        var name = unique("Test class ");

        var created = assets.createClass(new AssetClassForm(name, 7, new BigDecimal("10"), true));

        assertThat(created.assetAccountCode()).isEqualTo("%04d".formatted(next));
        assertThat(created.accdepAccountCode()).isEqualTo("%04d".formatted(next + 1));
        assertThat(created.lifeYears()).isEqualTo(7);
        assertThat(created.residualPercent()).isEqualByComparingTo("10");
        assertThat(assets.classes()).extracting(AssetClassView::id).contains(created.id());
        assertThat(jdbc.sql("SELECT name FROM account WHERE id = :id")
                        .param("id", created.accdepAccountId())
                        .query(String.class)
                        .single())
                .isEqualTo("Acc. depreciation – " + name.toLowerCase(Locale.ROOT));

        assertThatThrownBy(() -> assets.createClass(new AssetClassForm(name, 5, new BigDecimal("5"), true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A class with this name exists");
    }
}

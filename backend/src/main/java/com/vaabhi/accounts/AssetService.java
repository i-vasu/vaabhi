package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The fixed-asset register: asset classes, the assets bought on bills or brought in opening, and disposals. */
@Service
public class AssetService {

    private static final List<String> MODES = List.of("CASH", "UPI", "CARD", "BANK");
    private static final String SELECT = """
            SELECT a.id, a.name, a.asset_class_id, c.name AS class_name, a.cost_rupees, a.put_to_use_on,
                   a.opening_accdep_rupees,
                   COALESCE((SELECT SUM(d.amount_rupees) FROM asset_depreciation d
                             WHERE d.asset_id = a.id AND EXISTS (SELECT 1 FROM journal_entry j
                                    WHERE j.source_type = 'ASSET_DEPRECIATION' AND j.source_id = d.id)), 0)
                       AS posted_accdep_rupees,
                   EXISTS (SELECT 1 FROM asset_depreciation d
                           WHERE d.asset_id = a.id AND EXISTS (SELECT 1 FROM journal_entry j
                                  WHERE j.source_type = 'ASSET_DEPRECIATION' AND j.source_id = d.id))
                       AS has_posted_depreciation,
                   b.id AS bill_id, b.bill_no, a.disposed_on, a.disposal_proceeds_rupees
            FROM fixed_asset a
            JOIN asset_class c ON c.id = a.asset_class_id
            LEFT JOIN supplier_bill_line l ON l.id = a.bill_line_id
            LEFT JOIN supplier_bill b ON b.id = l.bill_id
            """;

    private final JdbcClient jdbc;
    private final ChartService chart;
    private final BooksSettingsService settings;
    private final DepreciationScheduler depreciation;

    AssetService(
            JdbcClient jdbc, ChartService chart, BooksSettingsService settings, DepreciationScheduler depreciation) {
        this.jdbc = jdbc;
        this.chart = chart;
        this.settings = settings;
        this.depreciation = depreciation;
    }

    private record Codes(String asset, String accdep) {}

    private record AssetRow(
            long id,
            String name,
            long assetClassId,
            String className,
            BigDecimal costRupees,
            LocalDate putToUseOn,
            BigDecimal openingAccdepRupees,
            BigDecimal postedAccdepRupees,
            boolean hasPostedDepreciation,
            @Nullable Long billId,
            @Nullable String billNo,
            @Nullable LocalDate disposedOn,
            @Nullable BigDecimal disposalProceedsRupees) {

        FixedAssetView toView() {
            var accumulated = openingAccdepRupees.add(postedAccdepRupees);
            return new FixedAssetView(
                    id,
                    name,
                    assetClassId,
                    className,
                    costRupees,
                    putToUseOn,
                    openingAccdepRupees,
                    Rupees.of(accumulated),
                    Rupees.of(costRupees.subtract(accumulated)),
                    billId,
                    billNo,
                    disposedOn,
                    disposalProceedsRupees,
                    hasPostedDepreciation);
        }
    }

    @Transactional(readOnly = true)
    public List<AssetClassView> classes() {
        return jdbc.sql("""
                        SELECT c.id, c.name, c.life_years, c.residual_percent, c.asset_account_id,
                               aa.code AS asset_account_code, c.accdep_account_id,
                               da.code AS accdep_account_code, c.active
                        FROM asset_class c
                        JOIN account aa ON aa.id = c.asset_account_id
                        JOIN account da ON da.id = c.accdep_account_id
                        ORDER BY c.id
                        """).query(AssetClassView.class).list();
    }

    /** Creates the class and the two asset accounts its depreciation posts against. */
    @Transactional
    public AssetClassView createClass(AssetClassForm form) {
        var name = form.name().trim();
        checkNameFree(name, -1);
        var codes = nextCodes();
        var assetAccount = chart.create(new AccountForm(codes.asset(), name, AccountType.ASSET, null, true));
        var accdepAccount = chart.create(new AccountForm(
                codes.accdep(), "Acc. depreciation – " + name.toLowerCase(Locale.ROOT), AccountType.ASSET, null, true));
        long id = jdbc.sql("""
                        INSERT INTO asset_class (name, life_years, residual_percent, asset_account_id, accdep_account_id,
                                                 active)
                        VALUES (:name, :life, :residual, :assetAccount, :accdepAccount, :active)
                        RETURNING id
                        """)
                .param("name", name)
                .param("life", form.lifeYears())
                .param("residual", Rupees.of(form.residualPercent()))
                .param("assetAccount", assetAccount.id())
                .param("accdepAccount", accdepAccount.id())
                .param("active", form.active())
                .query(Long.class)
                .single();
        return assetClass(id);
    }

    /** Renames the class or changes its life, residual or active flag. Life and residual only affect unposted months. */
    @Transactional
    public AssetClassView updateClass(long id, AssetClassForm form) {
        var current = assetClass(id);
        var name = form.name().trim();
        checkNameFree(name, id);
        boolean depreciationChanged = current.lifeYears() != form.lifeYears()
                || current.residualPercent().compareTo(form.residualPercent()) != 0;
        jdbc.sql("""
                        UPDATE asset_class
                        SET name = :name, life_years = :life, residual_percent = :residual, active = :active
                        WHERE id = :id
                        """)
                .param("name", name)
                .param("life", form.lifeYears())
                .param("residual", Rupees.of(form.residualPercent()))
                .param("active", form.active())
                .param("id", id)
                .update();
        if (depreciationChanged) {
            for (long assetId : jdbc.sql("SELECT id FROM fixed_asset WHERE asset_class_id = :id")
                    .param("id", id)
                    .query(Long.class)
                    .list()) {
                depreciation.refresh(assetId);
            }
        }
        return assetClass(id);
    }

    /** Every asset: the ones still in use first, newest put-to-use date first. */
    @Transactional(readOnly = true)
    public List<FixedAssetView> register() {
        return jdbc.sql(SELECT + " ORDER BY (a.disposed_on IS NOT NULL), a.put_to_use_on DESC, a.id DESC")
                .query(AssetRow.class)
                .list()
                .stream()
                .map(AssetRow::toView)
                .toList();
    }

    @Transactional
    public FixedAssetView createOpening(OpeningAssetForm form, @Nullable String actor) {
        var start = settings.get().startDate();
        if (start == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Set the books start date first");
        }
        if (!form.putToUseOn().isBefore(start)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Assets bought after the books start come from a bill");
        }
        assetClass(form.classId());
        var cost = Rupees.of(form.costRupees());
        var opening = Rupees.of(form.openingAccdepRupees());
        if (opening.compareTo(cost) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Opening depreciation can't be more than the cost");
        }
        long id = jdbc.sql("""
                        INSERT INTO fixed_asset (name, asset_class_id, cost_rupees, put_to_use_on,
                                                 opening_accdep_rupees, created_by)
                        VALUES (:name, :class, :cost, :putToUse, :opening, :by)
                        RETURNING id
                        """)
                .param("name", form.name().trim())
                .param("class", form.classId())
                .param("cost", cost)
                .param("putToUse", form.putToUseOn())
                .param("opening", opening)
                .param("by", actor)
                .query(Long.class)
                .single();
        depreciation.refresh(id);
        return asset(id);
    }

    @Transactional
    public FixedAssetView update(long id, AssetUpdateForm form) {
        var current = asset(id);
        boolean inputsChanged =
                current.classId() != form.classId() || !current.putToUseOn().equals(form.putToUseOn());
        if (current.hasPostedDepreciation() && inputsChanged) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This asset has depreciation in the books; only the name can change");
        }
        assetClass(form.classId());
        jdbc.sql("""
                        UPDATE fixed_asset SET name = :name, asset_class_id = :class, put_to_use_on = :putToUse
                        WHERE id = :id
                        """)
                .param("name", form.name().trim())
                .param("class", form.classId())
                .param("putToUse", form.putToUseOn())
                .param("id", id)
                .update();
        depreciation.refresh(id);
        return asset(id);
    }

    @Transactional
    public FixedAssetView dispose(long id, AssetDisposalForm form) {
        var current = asset(id);
        if (current.disposedOn() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already disposed");
        }
        if (form.disposedOn().isBefore(current.putToUseOn())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The disposal date is before the asset was put to use");
        }
        var proceeds = Rupees.of(form.proceedsRupees());
        if (proceeds.signum() > 0 && (form.mode() == null || !MODES.contains(form.mode()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick how the money came in");
        }
        jdbc.sql("""
                        UPDATE fixed_asset
                        SET disposed_on = :on, disposal_proceeds_rupees = :proceeds, disposal_mode = :mode
                        WHERE id = :id
                        """)
                .param("on", form.disposedOn())
                .param("proceeds", proceeds)
                .param("mode", proceeds.signum() > 0 ? form.mode() : null)
                .param("id", id)
                .update();
        depreciation.refresh(id);
        return asset(id);
    }

    private AssetClassView assetClass(long id) {
        return jdbc.sql("""
                        SELECT c.id, c.name, c.life_years, c.residual_percent, c.asset_account_id,
                               aa.code AS asset_account_code, c.accdep_account_id,
                               da.code AS accdep_account_code, c.active
                        FROM asset_class c
                        JOIN account aa ON aa.id = c.asset_account_id
                        JOIN account da ON da.id = c.accdep_account_id
                        WHERE c.id = :id
                        """).param("id", id).query(AssetClassView.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset class not found"));
    }

    private FixedAssetView asset(long id) {
        return jdbc.sql(SELECT + " WHERE a.id = :id")
                .param("id", id)
                .query(AssetRow.class)
                .optional()
                .map(AssetRow::toView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
    }

    private void checkNameFree(String name, long exceptId) {
        boolean taken = jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM asset_class WHERE lower(name) = lower(:name) AND id <> :id)")
                .param("name", name)
                .param("id", exceptId)
                .query(Boolean.class)
                .single();
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A class with this name exists");
        }
    }

    /** The next free pair of 15x0 and 15x1 account codes. */
    private Codes nextCodes() {
        int highest = jdbc.sql("SELECT COALESCE(MAX(CAST(code AS int)), 0) FROM account WHERE code ~ '^15[0-9]0$'")
                .query(Integer.class)
                .single();
        int next = highest == 0 ? 1500 : highest + 10;
        return new Codes("%04d".formatted(next), "%04d".formatted(next + 1));
    }
}

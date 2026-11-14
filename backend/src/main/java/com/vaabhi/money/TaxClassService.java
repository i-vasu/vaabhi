package com.vaabhi.money;

import com.vaabhi.settings.SettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The shop's tax classes: which HSN/SAC code and GST rate an item carries. */
@Service
public class TaxClassService {

    private static final String ID = "id";
    private static final String SELECT = """
            SELECT t.id, t.name, t.kind, t.hsn_code, t.rate_percent, t.slab_limit_rupees, t.rate_above_percent,
                   (t.id = :defaultId
                    OR EXISTS (SELECT 1 FROM category c WHERE c.tax_class_id = t.id)
                    OR EXISTS (SELECT 1 FROM garment_template g WHERE g.tax_class_id = t.id)
                    OR EXISTS (SELECT 1 FROM service_product s WHERE s.tax_class_id = t.id)) AS in_use
            FROM tax_class t
            """;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcClient jdbc;
    private final SettingsService settings;

    TaxClassService(JdbcClient jdbc, SettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    private long defaultId() {
        var id = settings.billing().defaultTaxClassId();
        return id == null ? -1L : id;
    }

    @Transactional(readOnly = true)
    public List<TaxClass> list() {
        return jdbc.sql(SELECT + " ORDER BY t.name")
                .param("defaultId", defaultId())
                .query(TaxClass.class)
                .list();
    }

    @Transactional(readOnly = true)
    public TaxClass get(long id) {
        return find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax class not found"));
    }

    private Optional<TaxClass> find(long id) {
        return jdbc.sql(SELECT + " WHERE t.id = :id")
                .param("defaultId", defaultId())
                .param(ID, id)
                .query(TaxClass.class)
                .optional();
    }

    @Transactional
    public TaxClass create(TaxClassForm form) {
        check(form);
        try {
            long id = jdbc.sql("""
                            INSERT INTO tax_class (name, kind, hsn_code, rate_percent, slab_limit_rupees,
                                rate_above_percent)
                            VALUES (:name, :kind, :hsn, :rate, :limit, :above) RETURNING id
                            """)
                    .param("name", form.name().trim())
                    .param("kind", form.kind().name())
                    .param("hsn", form.hsnCode())
                    .param("rate", form.ratePercent())
                    .param("limit", form.slabLimitRupees())
                    .param("above", form.rateAbovePercent())
                    .query(Long.class)
                    .single();
            return get(id);
        } catch (DuplicateKeyException e) {
            throw duplicate();
        }
    }

    @Transactional
    public TaxClass update(long id, TaxClassForm form) {
        check(form);
        int rows;
        try {
            rows = jdbc.sql("""
                            UPDATE tax_class SET name = :name, kind = :kind, hsn_code = :hsn, rate_percent = :rate,
                                slab_limit_rupees = :limit, rate_above_percent = :above
                            WHERE id = :id
                            """)
                    .param(ID, id)
                    .param("name", form.name().trim())
                    .param("kind", form.kind().name())
                    .param("hsn", form.hsnCode())
                    .param("rate", form.ratePercent())
                    .param("limit", form.slabLimitRupees())
                    .param("above", form.rateAbovePercent())
                    .update();
        } catch (DuplicateKeyException e) {
            throw duplicate();
        }
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax class not found");
        }
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (get(id).inUse()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This tax class is in use");
        }
        jdbc.sql("DELETE FROM tax_class WHERE id = :id").param(ID, id).update();
    }

    /**
     * The code and rate for one item: its own class, else the default class; null when there is neither. A piece goes
     * to the higher rate when its value before GST at the lower rate is above the slab limit.
     *
     * @param unitNetRupees the unit price: before GST when {@code inclusive} is false, GST included otherwise
     */
    @Transactional(readOnly = true)
    public @Nullable TaxRate resolve(@Nullable Long classId, BigDecimal unitNetRupees, boolean inclusive) {
        var taxClass = classId == null ? null : find(classId).orElse(null);
        if (taxClass == null) {
            long defaultId = defaultId();
            taxClass = defaultId < 0 ? null : find(defaultId).orElse(null);
        }
        if (taxClass == null) {
            return null;
        }
        var rate = taxClass.ratePercent();
        var limit = taxClass.slabLimitRupees();
        var above = taxClass.rateAbovePercent();
        if (limit != null && above != null) {
            var beforeTax = inclusive
                    ? unitNetRupees.multiply(HUNDRED).divide(HUNDRED.add(rate), 6, RoundingMode.HALF_UP)
                    : unitNetRupees;
            if (beforeTax.compareTo(limit) > 0) {
                rate = above;
            }
        }
        return new TaxRate(taxClass.hsnCode(), rate, taxClass.kind());
    }

    /** The class of a shop order item: its price-list item, else that item's garment, else the garment itself. */
    @Transactional(readOnly = true)
    public @Nullable Long shopItemClass(@Nullable Long templateId, @Nullable Long serviceProductId) {
        var classes = jdbc.sql("""
                        SELECT COALESCE(sp.tax_class_id, spt.tax_class_id, t.tax_class_id)
                        FROM (SELECT 1) one
                        LEFT JOIN service_product sp ON sp.id = CAST(:serviceProduct AS bigint)
                        LEFT JOIN garment_template spt ON spt.id = sp.template_id
                        LEFT JOIN garment_template t ON t.id = CAST(:template AS bigint)
                        """)
                .param("serviceProduct", serviceProductId)
                .param("template", templateId)
                .query(Long.class)
                .list();
        return classes.isEmpty() ? null : classes.get(0);
    }

    private static void check(TaxClassForm form) {
        if ((form.slabLimitRupees() == null) != (form.rateAbovePercent() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Give both the price limit and the rate above it, or neither");
        }
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A tax class with this name already exists");
    }
}

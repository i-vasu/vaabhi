package com.vaabhi.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lists other modules refer to by id: production stages, delivery packages and garment templates. Removing an
 * item only switches it off, so orders that used it keep their history.
 */
@Service
public class ShopListService {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String POSITION = "position";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    ShopListService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // --- production stages --------------------------------------------------------------------------------------

    /** Active stages in order. */
    @Transactional(readOnly = true)
    public List<ProductionStage> stages() {
        return jdbc.sql("SELECT id, name, kind FROM production_stage WHERE active ORDER BY position, id")
                .query(ProductionStage.class)
                .list();
    }

    /**
     * Replaces the stage list. At least one WORK stage, then exactly one READY and one DELIVERED stage, last and
     * in that order; names must be unique.
     */
    @Transactional
    List<ProductionStage> saveStages(List<ProductionStage> stages) {
        var kinds = stages.stream().map(ProductionStage::kind).toList();
        int n = kinds.size();
        boolean valid = n >= 3
                && kinds.get(n - 2) == ProductionStage.Kind.READY
                && kinds.get(n - 1) == ProductionStage.Kind.DELIVERED
                && kinds.subList(0, n - 2).stream().allMatch(kind -> kind == ProductionStage.Kind.WORK);
        if (!valid) {
            throw bad("Add at least one work stage; Ready and Delivered must be the last two stages");
        }
        requireUniqueNames(stages.stream().map(ProductionStage::name).toList());

        var keep = new HashSet<Long>();
        for (int i = 0; i < n; i++) {
            var stage = stages.get(i);
            if (stage.id() != null && exists("production_stage", stage.id())) {
                // A stage keeps its kind for life: orders already sitting in it depend on what it means.
                int updated = jdbc.sql("UPDATE production_stage SET name = :name, position = :position, active = TRUE"
                                + " WHERE id = :id AND kind = :kind")
                        .param(NAME, stage.name().trim())
                        .param("kind", stage.kind().name())
                        .param(POSITION, i + 1)
                        .param(ID, stage.id())
                        .update();
                if (updated == 0) {
                    throw bad("A stage cannot change between work, ready and delivered");
                }
                keep.add(stage.id());
            } else if (stage.kind() == ProductionStage.Kind.WORK) {
                keep.add(
                        jdbc.sql("INSERT INTO production_stage (name, kind, position) VALUES (:name, 'WORK', :position)"
                                        + " RETURNING id")
                                .param(NAME, stage.name().trim())
                                .param(POSITION, i + 1)
                                .query(Long.class)
                                .single());
            } else {
                throw bad("Ready and Delivered stages can be renamed but not added again");
            }
        }
        deactivateOthers("production_stage", keep);
        return stages();
    }

    // --- delivery packages --------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DeliveryPackage> deliveryPackages() {
        return jdbc.sql("SELECT id, name, price_rupees, days FROM delivery_package WHERE active ORDER BY position, id")
                .query(DeliveryPackage.class)
                .list();
    }

    @Transactional
    List<DeliveryPackage> saveDeliveryPackages(List<DeliveryPackage> packages) {
        requireUniqueNames(packages.stream().map(DeliveryPackage::name).toList());
        var keep = new HashSet<Long>();
        for (int i = 0; i < packages.size(); i++) {
            var pkg = packages.get(i);
            if (pkg.id() != null && exists("delivery_package", pkg.id())) {
                jdbc.sql("UPDATE delivery_package SET name = :name, price_rupees = :price, days = :days,"
                                + " position = :position, active = TRUE WHERE id = :id")
                        .param(NAME, pkg.name().trim())
                        .param("price", pkg.priceRupees())
                        .param("days", pkg.days())
                        .param(POSITION, i + 1)
                        .param(ID, pkg.id())
                        .update();
                keep.add(pkg.id());
            } else {
                keep.add(jdbc.sql("INSERT INTO delivery_package (name, price_rupees, days, position)"
                                + " VALUES (:name, :price, :days, :position) RETURNING id")
                        .param(NAME, pkg.name().trim())
                        .param("price", pkg.priceRupees())
                        .param("days", pkg.days())
                        .param(POSITION, i + 1)
                        .query(Long.class)
                        .single());
            }
        }
        deactivateOthers("delivery_package", keep);
        return deliveryPackages();
    }

    // --- garment templates --------------------------------------------------------------------------------------

    private record TemplateRow(
            long id,
            String name,
            GarmentTemplate.Audience businessType,
            String fields,
            @Nullable Long taxClassId) {}

    private static final TypeReference<List<GarmentTemplate.Field>> FIELDS = new TypeReference<>() {};

    @Transactional(readOnly = true)
    public List<GarmentTemplate> garmentTemplates() {
        return jdbc
                .sql("SELECT id, name, business_type, fields::text AS fields, tax_class_id FROM garment_template"
                        + " WHERE active ORDER BY position, id")
                .query(TemplateRow.class)
                .list()
                .stream()
                .map(row -> new GarmentTemplate(
                        row.id(),
                        row.name(),
                        row.businessType(),
                        json.readValue(row.fields(), FIELDS),
                        row.taxClassId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GarmentTemplate garmentTemplate(long id) {
        return garmentTemplates().stream()
                .filter(template -> template.id() != null && template.id() == id)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Garment template not found"));
    }

    @Transactional(readOnly = true)
    void requireTaxClass(@Nullable Long taxClassId) {
        if (taxClassId != null
                && !jdbc.sql("SELECT EXISTS (SELECT 1 FROM tax_class WHERE id = :id)")
                        .param(ID, taxClassId)
                        .query(Boolean.class)
                        .single()) {
            throw bad("Unknown tax class");
        }
    }

    @Transactional
    GarmentTemplate saveGarmentTemplate(@Nullable Long id, GarmentTemplate template) {
        requireUniqueNames(
                template.fields().stream().map(GarmentTemplate.Field::key).toList());
        if (template.taxClassId() != null
                && !jdbc.sql("SELECT EXISTS (SELECT 1 FROM tax_class WHERE id = :id)")
                        .param(ID, template.taxClassId())
                        .query(Boolean.class)
                        .single()) {
            throw bad("Unknown tax class");
        }
        var clash = jdbc.sql("SELECT id FROM garment_template WHERE active AND lower(name) = lower(:name)")
                .param(NAME, template.name().trim())
                .query(Long.class)
                .optional();
        if (clash.isPresent() && !clash.get().equals(id)) {
            throw bad("A garment template called " + template.name().trim() + " already exists");
        }
        // A switched-off template with the same name is reused rather than tripping the unique index.
        var revived = id == null
                ? jdbc.sql("SELECT id FROM garment_template WHERE NOT active AND lower(name) = lower(:name)")
                        .param(NAME, template.name().trim())
                        .query(Long.class)
                        .optional()
                        .orElse(null)
                : id;
        String fields = json.writeValueAsString(template.fields());
        long savedId;
        if (revived != null) {
            int updated = jdbc.sql("UPDATE garment_template SET name = :name, business_type = :type,"
                            + " fields = CAST(:fields AS jsonb), tax_class_id = :taxClass, active = TRUE"
                            + " WHERE id = :id")
                    .param(NAME, template.name().trim())
                    .param("type", template.businessType().name())
                    .param("fields", fields)
                    .param("taxClass", template.taxClassId())
                    .param(ID, revived)
                    .update();
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Garment template not found");
            }
            savedId = revived;
        } else {
            savedId = jdbc.sql("INSERT INTO garment_template (name, business_type, fields, tax_class_id, position)"
                            + " VALUES (:name, :type, CAST(:fields AS jsonb), :taxClass,"
                            + " (SELECT COALESCE(MAX(position), 0) + 1 FROM garment_template)) RETURNING id")
                    .param(NAME, template.name().trim())
                    .param("type", template.businessType().name())
                    .param("fields", fields)
                    .param("taxClass", template.taxClassId())
                    .query(Long.class)
                    .single();
        }
        return garmentTemplate(savedId);
    }

    @Transactional
    void removeGarmentTemplate(long id) {
        if (jdbc.sql("UPDATE garment_template SET active = FALSE WHERE id = :id AND active")
                        .param(ID, id)
                        .update()
                == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Garment template not found");
        }
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    /** Table names are constants from this class, never user input. */
    // Only constant SQL fragments (and fixed table names / enum-chosen ORDER BY) are joined; values are bound.
    @SuppressWarnings("java:S2077")
    private boolean exists(String table, long id) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE id = :id")
                        .param(ID, id)
                        .query(Long.class)
                        .single()
                > 0;
    }

    // Only constant SQL fragments (and fixed table names / enum-chosen ORDER BY) are joined; values are bound.
    @SuppressWarnings("java:S2077")
    private void deactivateOthers(String table, java.util.Set<Long> keep) {
        jdbc.sql("UPDATE " + table + " SET active = FALSE WHERE active AND NOT (id = ANY(:keep))")
                .param("keep", keep.toArray(Long[]::new))
                .update();
    }

    private static void requireUniqueNames(List<String> names) {
        var seen = new HashSet<String>();
        for (var name : names) {
            if (!seen.add(name.trim().toLowerCase(Locale.ROOT))) {
                throw bad(name.trim() + " appears twice");
            }
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

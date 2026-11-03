package com.vaabhi.tailoring;

import com.vaabhi.settings.NumberSequence;
import com.vaabhi.settings.NumberingService;
import com.vaabhi.settings.ProductionStage;
import com.vaabhi.settings.ShopListService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The stitching price list. Removing a product switches it off so past orders keep it. */
@Service
public class ServiceProductService {

    private static final String ID = "id";
    private static final TypeReference<List<StageCost>> COSTS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final ShopListService lists;
    private final NumberingService numbering;

    ServiceProductService(JdbcClient jdbc, JsonMapper json, ShopListService lists, NumberingService numbering) {
        this.jdbc = jdbc;
        this.json = json;
        this.lists = lists;
        this.numbering = numbering;
    }

    private record Row(
            long id,
            long templateId,
            String garment,
            String code,
            String name,
            BigDecimal priceRupees,
            @Nullable String photoKey,
            String stageCosts) {}

    private static final String SELECT = """
            SELECT p.id, p.template_id, t.name AS garment, p.code, p.name, p.price_rupees, p.photo_key,
                   p.stage_costs::text AS stage_costs
            FROM service_product p JOIN garment_template t ON t.id = p.template_id
            """;

    private ServiceProduct toProduct(Row row) {
        var costs = json.readValue(row.stageCosts(), COSTS);
        var totalCost = Rupees.sum(costs, StageCost::rupees);
        return new ServiceProduct(
                row.id(),
                row.templateId(),
                row.garment(),
                row.code(),
                row.name(),
                row.priceRupees(),
                row.photoKey(),
                costs,
                row.priceRupees().subtract(totalCost));
    }

    /** Active products, optionally of one garment or matching a search (name or code). */
    @Transactional(readOnly = true)
    public List<ServiceProduct> list(@Nullable Long templateId, @Nullable String query) {
        return jdbc
                .sql(SELECT + """
                        WHERE p.active
                          AND (CAST(:template AS bigint) IS NULL OR p.template_id = :template)
                          AND (CAST(:q AS varchar) IS NULL OR lower(p.name) LIKE :q OR lower(p.code) LIKE :q
                               OR lower(t.name) LIKE :q)
                        ORDER BY t.position, lower(p.name)
                        """)
                .param("template", templateId)
                .param("q", ClientService.like(query))
                .query(Row.class)
                .list()
                .stream()
                .map(this::toProduct)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceProduct get(long id) {
        return jdbc.sql(SELECT + " WHERE p.id = :id")
                .param(ID, id)
                .query(Row.class)
                .optional()
                .map(this::toProduct)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @Transactional
    public ServiceProduct save(@Nullable Long id, ServiceProductForm form) {
        lists.garmentTemplate(form.templateId());
        requireKnownStages(form.stageCosts());
        var code = form.code() == null || form.code().isBlank()
                ? (id == null ? numbering.next(NumberSequence.Kind.ITEM) : get(id).code())
                : form.code().trim();
        var costs = json.writeValueAsString(form.stageCosts().stream()
                .filter(cost -> cost.rupees().signum() > 0)
                .toList());
        try {
            if (id == null) {
                id = jdbc.sql("""
                                INSERT INTO service_product (template_id, code, name, price_rupees, photo_key, stage_costs)
                                VALUES (:template, :code, :name, :price, :photo, CAST(:costs AS jsonb)) RETURNING id
                                """)
                        .param("template", form.templateId())
                        .param("code", code)
                        .param("name", form.name().trim())
                        .param("price", form.priceRupees())
                        .param("photo", form.photoKey())
                        .param("costs", costs)
                        .query(Long.class)
                        .single();
            } else if (jdbc.sql("""
                                    UPDATE service_product SET template_id = :template, code = :code, name = :name,
                                        price_rupees = :price, photo_key = :photo, stage_costs = CAST(:costs AS jsonb)
                                    WHERE id = :id AND active
                                    """)
                            .param("template", form.templateId())
                            .param("code", code)
                            .param("name", form.name().trim())
                            .param("price", form.priceRupees())
                            .param("photo", form.photoKey())
                            .param("costs", costs)
                            .param(ID, id)
                            .update()
                    == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product ID " + code + " is already used", e);
        }
        return get(id);
    }

    @Transactional
    public void remove(long id) {
        if (jdbc.sql("UPDATE service_product SET active = FALSE WHERE id = :id AND active")
                        .param(ID, id)
                        .update()
                == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    private void requireKnownStages(List<StageCost> costs) {
        Set<Long> work = lists.stages().stream()
                .filter(stage -> stage.kind() == ProductionStage.Kind.WORK)
                .map(ProductionStage::id)
                .collect(Collectors.toSet());
        if (costs.stream().anyMatch(cost -> !work.contains(cost.stageId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Costs can only be set for work stages");
        }
    }
}

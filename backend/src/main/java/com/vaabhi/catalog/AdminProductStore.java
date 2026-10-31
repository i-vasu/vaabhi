package com.vaabhi.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** SQL for the admin side of the catalogue. Every statement is static; all values are bind parameters. */
@Component
class AdminProductStore {

    private final JdbcClient jdbc;

    AdminProductStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // --- list ----------------------------------------------------------------------------------------------

    private static final String LIST_FILTER = """
            FROM product p
            JOIN category c ON c.id = p.category_id
            WHERE (CAST(:status AS varchar) IS NULL OR p.status = :status)
              AND (CAST(:category AS varchar) IS NULL OR c.slug = :category)
              AND (CAST(:q AS varchar) IS NULL OR p.name ILIKE :q OR p.slug ILIKE :q
                   OR EXISTS (SELECT 1 FROM product_variant v WHERE v.product_id = p.id AND v.sku ILIKE :q))
            """;

    private record SummaryRow(
            long id,
            String slug,
            String name,
            String categorySlug,
            String categoryName,
            String status,
            int variantCount,
            long stockTotal,
            @Nullable Integer minPricePaise,
            OffsetDateTime updatedAt) {}

    AdminProductPage list(
            @Nullable ProductStatus status, @Nullable String category, @Nullable String query, int page, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", status == null ? null : status.name());
        params.put("category", category);
        params.put("q", query == null || query.isBlank() ? null : "%" + escapeLike(query.trim()) + "%");
        var total = jdbc.sql("SELECT COUNT(*) " + LIST_FILTER)
                .params(params)
                .query(Long.class)
                .single();
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);
        var items = jdbc
                .sql("""
                        SELECT p.id, p.slug, p.name, c.slug AS category_slug, c.name AS category_name, p.status,
                               (SELECT COUNT(*) FROM product_variant v WHERE v.product_id = p.id AND v.active)
                                   AS variant_count,
                               (SELECT COALESCE(SUM(v.stock_qty), 0) FROM product_variant v
                                WHERE v.product_id = p.id AND v.active) AS stock_total,
                               (SELECT MIN(v.price_paise) FROM product_variant v
                                WHERE v.product_id = p.id AND v.active) AS min_price_paise,
                               p.updated_at
                        """ + LIST_FILTER + " ORDER BY p.updated_at DESC, p.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query(SummaryRow.class)
                .list()
                .stream()
                .map(r -> new AdminProductSummary(
                        r.id(),
                        r.slug(),
                        r.name(),
                        new CategoryRef(r.categorySlug(), r.categoryName()),
                        ProductStatus.valueOf(r.status()),
                        r.variantCount(),
                        r.stockTotal(),
                        r.minPricePaise(),
                        r.updatedAt()))
                .toList();
        return new AdminProductPage(items, total, page, pageSize);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // --- read one ------------------------------------------------------------------------------------------

    private record ProductRow(
            long id,
            String slug,
            String name,
            @Nullable String subtitle,
            @Nullable String description,
            String categorySlug,
            String status,
            @Nullable String badge,
            @Nullable String careInstructions,
            @Nullable BigDecimal sareeLengthM,
            boolean blouseIncluded,
            @Nullable BigDecimal blouseLengthM,
            @Nullable OffsetDateTime publishedAt,
            OffsetDateTime updatedAt) {}

    Optional<AdminProduct> find(long id) {
        return jdbc.sql("""
                        SELECT p.id, p.slug, p.name, p.subtitle, p.description, c.slug AS category_slug, p.status,
                               p.badge, p.care_instructions, p.saree_length_m, p.blouse_included, p.blouse_length_m,
                               p.published_at, p.updated_at
                        FROM product p JOIN category c ON c.id = p.category_id
                        WHERE p.id = :id
                        """)
                .param("id", id)
                .query(ProductRow.class)
                .optional()
                .map(r -> new AdminProduct(
                        r.id(),
                        r.slug(),
                        r.name(),
                        r.subtitle(),
                        r.description(),
                        r.categorySlug(),
                        ProductStatus.valueOf(r.status()),
                        r.badge() == null ? null : Badge.valueOf(r.badge()),
                        r.careInstructions(),
                        r.sareeLengthM(),
                        r.blouseIncluded(),
                        r.blouseLengthM(),
                        attributes(id),
                        variants(id),
                        addons(id),
                        media(id),
                        r.publishedAt(),
                        r.updatedAt()));
    }

    private Map<String, List<String>> attributes(long productId) {
        record Row(String facetCode, String valueCode) {}

        Map<String, List<String>> attributes = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT f.code AS facet_code, fv.code AS value_code
                        FROM product_facet_value pfv
                        JOIN facet_value fv ON fv.id = pfv.facet_value_id
                        JOIN facet f ON f.id = fv.facet_id
                        WHERE pfv.product_id = :id
                        ORDER BY f.position, fv.position
                        """)
                .param("id", productId)
                .query(Row.class)
                .list()
                .forEach(r -> attributes
                        .computeIfAbsent(r.facetCode(), k -> new ArrayList<>())
                        .add(r.valueCode()));
        return attributes;
    }

    private List<VariantForm> variants(long productId) {
        return jdbc.sql("""
                        SELECT sku, colour_name, colour_hex, colour_family, size, price_paise, mrp_paise, stock_qty, active
                        FROM product_variant WHERE product_id = :id ORDER BY position, id
                        """).param("id", productId).query(VariantForm.class).list();
    }

    private List<AddonForm> addons(long productId) {
        return jdbc.sql("""
                        SELECT code, kind, name, description, price_paise, input, active
                        FROM product_addon WHERE product_id = :id ORDER BY position, id
                        """).param("id", productId).query(AddonForm.class).list();
    }

    private List<MediaResponse> media(long productId) {
        return jdbc.sql("""
                        SELECT kind, storage_key AS key, alt_text AS alt, colour_name, width, height
                        FROM product_media WHERE product_id = :id ORDER BY position, id
                        """).param("id", productId).query(MediaResponse.class).list();
    }

    // --- checks --------------------------------------------------------------------------------------------

    Optional<Long> categoryId(String slug) {
        return jdbc.sql("SELECT id FROM category WHERE slug = :slug")
                .param("slug", slug)
                .query(Long.class)
                .optional();
    }

    boolean slugTaken(String slug, @Nullable Long exceptProductId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM product WHERE slug = :slug AND id <> COALESCE(:except, -1))")
                .param("slug", slug)
                .param("except", exceptProductId)
                .query(Boolean.class)
                .single();
    }

    /** SKUs from the list that already belong to another product. */
    List<String> skusUsedElsewhere(List<String> skus, @Nullable Long exceptProductId) {
        if (skus.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT sku FROM product_variant WHERE sku IN (:skus) AND product_id <> COALESCE(:except, -1)")
                .param("skus", skus)
                .param("except", exceptProductId)
                .query(String.class)
                .list();
    }

    // --- writes --------------------------------------------------------------------------------------------

    long insert(ProductForm form, String slug, long categoryId) {
        return jdbc.sql("""
                        INSERT INTO product (slug, name, subtitle, description, category_id, status, badge,
                                             care_instructions, saree_length_m, blouse_included, blouse_length_m)
                        VALUES (:slug, :name, :subtitle, :description, :categoryId, 'DRAFT', :badge,
                                :care, :sareeLength, :blouseIncluded, :blouseLength)
                        RETURNING id
                        """)
                .params(productParams(form, slug, categoryId))
                .query(Long.class)
                .single();
    }

    boolean update(long id, ProductForm form, String slug, long categoryId) {
        var params = productParams(form, slug, categoryId);
        params.put("id", id);
        return jdbc.sql("""
                        UPDATE product SET slug = :slug, name = :name, subtitle = :subtitle, description = :description,
                               category_id = :categoryId, badge = :badge, care_instructions = :care,
                               saree_length_m = :sareeLength, blouse_included = :blouseIncluded,
                               blouse_length_m = :blouseLength, updated_at = now()
                        WHERE id = :id
                        """).params(params).update() > 0;
    }

    private static Map<String, Object> productParams(ProductForm form, String slug, long categoryId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("slug", slug);
        params.put("name", form.name().trim());
        params.put("subtitle", blankToNull(form.subtitle()));
        params.put("description", blankToNull(form.description()));
        params.put("categoryId", categoryId);
        params.put("badge", form.badge() == null ? null : form.badge().name());
        params.put("care", blankToNull(form.careInstructions()));
        params.put("sareeLength", form.sareeLengthM());
        params.put("blouseIncluded", form.blouseIncluded());
        params.put("blouseLength", form.blouseLengthM());
        return params;
    }

    /**
     * Replaces variants, attributes and add-ons. Variants are matched by SKU so their ids stay stable; variants
     * left out are deleted (no orders reference them yet; the orders module will switch this to deactivation).
     */
    void replaceChildren(
            long productId, List<VariantForm> variants, Map<Long, List<String>> facetValues, List<AddonForm> addons) {
        var skus = variants.stream().map(VariantForm::sku).toList();
        jdbc.sql("DELETE FROM product_variant WHERE product_id = :id AND sku <> ALL (CAST(:skus AS varchar[]))")
                .param("id", productId)
                .param("skus", skus.toArray(String[]::new))
                .update();
        for (var i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            jdbc.sql("""
                            INSERT INTO product_variant (product_id, sku, colour_name, colour_hex, colour_family, size,
                                                         price_paise, mrp_paise, stock_qty, active, position)
                            VALUES (:productId, :sku, :colourName, :colourHex, :colourFamily, :size,
                                    :price, :mrp, :stock, :active, :position)
                            ON CONFLICT (sku) DO UPDATE SET colour_name = EXCLUDED.colour_name,
                                colour_hex = EXCLUDED.colour_hex, colour_family = EXCLUDED.colour_family,
                                size = EXCLUDED.size, price_paise = EXCLUDED.price_paise,
                                mrp_paise = EXCLUDED.mrp_paise, stock_qty = EXCLUDED.stock_qty,
                                active = EXCLUDED.active, position = EXCLUDED.position
                            WHERE product_variant.product_id = EXCLUDED.product_id
                            """)
                    .param("productId", productId)
                    .param("sku", v.sku())
                    .param("colourName", v.colourName().trim())
                    .param("colourHex", v.colourHex().toUpperCase(java.util.Locale.ROOT))
                    .param("colourFamily", v.colourFamily())
                    .param("size", blankToNull(v.size()))
                    .param("price", v.pricePaise())
                    .param("mrp", v.mrpPaise())
                    .param("stock", v.stockQty())
                    .param("active", v.active())
                    .param("position", i)
                    .update();
        }

        jdbc.sql("DELETE FROM product_facet_value WHERE product_id = :id")
                .param("id", productId)
                .update();
        facetValues.forEach((facetId, codes) -> jdbc.sql("""
                        INSERT INTO product_facet_value (product_id, facet_value_id)
                        SELECT :productId, fv.id FROM facet_value fv WHERE fv.facet_id = :facetId AND fv.code IN (:codes)
                        """)
                .param("productId", productId)
                .param("facetId", facetId)
                .param("codes", codes)
                .update());

        jdbc.sql("DELETE FROM product_addon WHERE product_id = :id")
                .param("id", productId)
                .update();
        for (var i = 0; i < addons.size(); i++) {
            var a = addons.get(i);
            jdbc.sql("""
                            INSERT INTO product_addon (product_id, kind, code, name, description, price_paise, input,
                                                       active, position)
                            VALUES (:productId, :kind, :code, :name, :description, :price, :input, :active, :position)
                            """)
                    .param("productId", productId)
                    .param("kind", a.kind().name())
                    .param("code", a.code())
                    .param("name", a.name().trim())
                    .param("description", blankToNull(a.description()))
                    .param("price", a.pricePaise())
                    .param("input", a.input().name())
                    .param("active", a.active())
                    .param("position", i)
                    .update();
        }
    }

    boolean setStatus(long id, ProductStatus status) {
        return jdbc.sql("""
                        UPDATE product SET status = :status, updated_at = now(),
                               published_at = CASE WHEN :status = 'PUBLISHED' THEN COALESCE(published_at, now())
                                                   ELSE published_at END
                        WHERE id = :id
                        """).param("id", id).param("status", status.name()).update() > 0;
    }

    int activeVariantCount(long productId) {
        return jdbc.sql("SELECT COUNT(*) FROM product_variant WHERE product_id = :id AND active AND price_paise > 0")
                .param("id", productId)
                .query(Integer.class)
                .single();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

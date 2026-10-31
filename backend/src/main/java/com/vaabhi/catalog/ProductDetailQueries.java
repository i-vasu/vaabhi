package com.vaabhi.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read model for the product page. Only published products with at least one active variant are visible. */
@Component
class ProductDetailQueries {

    private final JdbcClient jdbc;

    ProductDetailQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record ProductRow(
            long id,
            String slug,
            String name,
            @Nullable String subtitle,
            @Nullable String description,
            @Nullable String badge,
            @Nullable String careInstructions,
            @Nullable BigDecimal sareeLengthM,
            boolean blouseIncluded,
            @Nullable BigDecimal blouseLengthM,
            String categorySlug,
            String categoryName) {}

    private record VariantRow(
            String sku,
            String colourName,
            String colourHex,
            @Nullable String size,
            int pricePaise,
            int mrpPaise,
            int stockQty) {}

    Optional<ProductDetailResponse> find(String slug) {
        var product = jdbc.sql("""
                        SELECT p.id, p.slug, p.name, p.subtitle, p.description, p.badge, p.care_instructions,
                               p.saree_length_m, p.blouse_included, p.blouse_length_m,
                               c.slug AS category_slug, c.name AS category_name
                        FROM product p
                        JOIN category c ON c.id = p.category_id
                        WHERE p.slug = :slug AND p.status = 'PUBLISHED'
                        """).param("slug", slug).query(ProductRow.class).optional();
        if (product.isEmpty()) {
            return Optional.empty();
        }
        var row = product.get();

        var variantRows =
                jdbc.sql("""
                        SELECT sku, colour_name, colour_hex, size, price_paise, mrp_paise, stock_qty
                        FROM product_variant
                        WHERE product_id = :id AND active
                        ORDER BY position, id
                        """).param("id", row.id()).query(VariantRow.class).list();
        if (variantRows.isEmpty()) {
            return Optional.empty();
        }
        var cheapest = variantRows.stream()
                .min((a, b) -> Integer.compare(a.pricePaise(), b.pricePaise()))
                .orElseThrow();
        var variants = variantRows.stream()
                .map(v -> new VariantResponse(
                        v.sku(),
                        v.colourName(),
                        v.colourHex(),
                        v.size(),
                        v.pricePaise(),
                        v.mrpPaise(),
                        v.stockQty() > 0,
                        Stock.stockLeft(v.stockQty())))
                .toList();

        return Optional.of(new ProductDetailResponse(
                row.slug(),
                row.name(),
                row.subtitle(),
                row.description(),
                row.badge() == null ? null : Badge.valueOf(row.badge()),
                new CategoryRef(row.categorySlug(), row.categoryName()),
                cheapest.pricePaise(),
                cheapest.mrpPaise(),
                Stock.discountPercent(cheapest.pricePaise(), cheapest.mrpPaise()),
                row.careInstructions(),
                row.sareeLengthM(),
                row.blouseIncluded(),
                row.blouseLengthM(),
                attributes(row.id()),
                variants,
                media(row.id()),
                addons(row.id())));
    }

    private List<AttributeResponse> attributes(long productId) {
        record Row(String facetCode, String facetName, String valueCode, String valueName) {}

        Map<String, AttributeResponse> attributes = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT f.code AS facet_code, f.name AS facet_name, fv.code AS value_code, fv.name AS value_name
                        FROM product_facet_value pfv
                        JOIN facet_value fv ON fv.id = pfv.facet_value_id
                        JOIN facet f ON f.id = fv.facet_id
                        WHERE pfv.product_id = :id
                        ORDER BY f.position, f.id, fv.position, fv.id
                        """)
                .param("id", productId)
                .query(Row.class)
                .list()
                .forEach(r -> attributes
                        .computeIfAbsent(
                                r.facetCode(), code -> new AttributeResponse(code, r.facetName(), new ArrayList<>()))
                        .values()
                        .add(new AttributeResponse.AttributeValue(r.valueCode(), r.valueName())));
        return attributes.values().stream()
                .map(a -> new AttributeResponse(a.code(), a.name(), List.copyOf(a.values())))
                .toList();
    }

    private List<MediaResponse> media(long productId) {
        record Row(
                String kind,
                String storageKey,
                String altText,
                @Nullable String colourName,
                @Nullable Integer width,
                @Nullable Integer height) {}

        return jdbc.sql("""
                        SELECT kind, storage_key, alt_text, colour_name, width, height
                        FROM product_media
                        WHERE product_id = :id AND status = 'READY'
                        ORDER BY position, id
                        """).param("id", productId).query(Row.class).list().stream()
                .map(r -> new MediaResponse(
                        MediaKind.valueOf(r.kind()),
                        r.storageKey(),
                        r.altText(),
                        r.colourName(),
                        r.width(),
                        r.height()))
                .toList();
    }

    private List<AddonResponse> addons(long productId) {
        record Row(
                String code,
                String kind,
                String name,
                @Nullable String description,
                int pricePaise,
                String input) {}

        return jdbc.sql("""
                        SELECT code, kind, name, description, price_paise, input
                        FROM product_addon
                        WHERE product_id = :id AND active
                        ORDER BY position, id
                        """).param("id", productId).query(Row.class).list().stream()
                .map(r -> new AddonResponse(
                        r.code(),
                        AddonKind.valueOf(r.kind()),
                        r.name(),
                        r.description(),
                        r.pricePaise(),
                        AddonInput.valueOf(r.input())))
                .toList();
    }
}

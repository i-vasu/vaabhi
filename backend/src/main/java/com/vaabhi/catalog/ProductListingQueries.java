package com.vaabhi.catalog;

import com.vaabhi.catalog.ListingFilters.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read model for storefront listings: two queries for the page and its filters, two for card details. */
@Component
class ProductListingQueries {

    /** Published products of one category with their cheapest active variant's price and total stock. */
    private static final String LISTED = """
            WITH listed AS (
                SELECT p.id, p.slug, p.name, p.subtitle, p.badge, p.popularity, p.published_at,
                       cheapest.price_paise, cheapest.mrp_paise, stock.stock_total, stock.in_stock
                FROM product p
                JOIN LATERAL (
                    SELECT v.price_paise, v.mrp_paise FROM product_variant v
                    WHERE v.product_id = p.id AND v.active
                    ORDER BY v.price_paise, v.position
                    LIMIT 1
                ) cheapest ON TRUE
                JOIN LATERAL (
                    SELECT SUM(v.stock_qty) AS stock_total, BOOL_OR(v.stock_qty > 0) AS in_stock
                    FROM product_variant v
                    WHERE v.product_id = p.id AND v.active
                ) stock ON TRUE
                WHERE p.status = 'PUBLISHED' AND p.category_id = :categoryId
            )
            """;

    private static final String AND = " AND ";
    private static final String COLOUR = "colour";
    private static final String SIZE = "size";

    private static final List<String> SIZE_ORDER = List.of("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL");

    private final JdbcClient jdbc;

    ProductListingQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    ProductListingResponse list(CategoryResponse category, ListingQuery query, FacetCatalog.Definitions definitions) {
        var filters = new ListingFilters(category.id(), query, definitions);
        var counts = counts(filters);
        var items = items(filters, query);
        return new ProductListingResponse(
                category,
                items,
                counts.total(),
                query.page(),
                query.pageSize(),
                query.sort().param(),
                filterGroups(query, definitions, counts),
                counts.priceRange());
    }

    // --- items -------------------------------------------------------------------------------------------

    private record ItemRow(
            long id,
            String slug,
            String name,
            @Nullable String subtitle,
            @Nullable String badge,
            int pricePaise,
            int mrpPaise,
            long stockTotal,
            boolean inStock) {}

    private List<ProductSummary> items(ListingFilters filters, ListingQuery query) {
        var params = new HashMap<>(filters.params());
        params.put("limit", query.pageSize());
        params.put("offset", query.offset());
        // Reviewed for SQL injection (java:S2077): the SQL text is built only from constants, the ProductSort enum
        // and generated parameter names (ListingFilters); every user-supplied value is a bind parameter.
        var sql = LISTED
                + "SELECT l.id, l.slug, l.name, l.subtitle, l.badge, l.price_paise, l.mrp_paise,"
                + " l.stock_total, l.in_stock FROM listed l WHERE "
                + filters.where(null)
                + " ORDER BY "
                + query.sort().orderBy()
                + " LIMIT :limit OFFSET :offset";
        var rows = jdbc.sql(sql) // NOSONAR
                .params(params)
                .query(ItemRow.class)
                .list();
        if (rows.isEmpty()) {
            return List.of();
        }

        var ids = rows.stream().map(ItemRow::id).toList();
        var swatches = swatches(ids);
        var images = images(ids);
        return rows.stream()
                .map(row -> new ProductSummary(
                        row.slug(),
                        row.name(),
                        row.subtitle(),
                        row.badge() == null ? null : Badge.valueOf(row.badge()),
                        row.pricePaise(),
                        row.mrpPaise(),
                        Stock.discountPercent(row.pricePaise(), row.mrpPaise()),
                        swatches.getOrDefault(row.id(), List.of()),
                        row.inStock(),
                        Stock.stockLeft(row.stockTotal()),
                        images.get(row.id())))
                .toList();
    }

    private Map<Long, List<ColourSwatch>> swatches(List<Long> productIds) {
        record Row(long productId, String colourName, String colourHex) {}

        Map<Long, List<ColourSwatch>> swatches = new HashMap<>();
        jdbc.sql("""
                        SELECT product_id, colour_name, colour_hex
                        FROM product_variant
                        WHERE active AND product_id IN (:ids)
                        GROUP BY product_id, colour_name, colour_hex
                        ORDER BY product_id, MIN(position)
                        """)
                .param("ids", productIds)
                .query(Row.class)
                .list()
                .forEach(row -> swatches.computeIfAbsent(row.productId(), id -> new ArrayList<>())
                        .add(new ColourSwatch(row.colourName(), row.colourHex())));
        return swatches;
    }

    private Map<Long, MediaResponse> images(List<Long> productIds) {
        record Row(
                long productId,
                String storageKey,
                String altText,
                @Nullable String colourName,
                @Nullable Integer width,
                @Nullable Integer height) {}

        Map<Long, MediaResponse> images = new HashMap<>();
        jdbc.sql("""
                        SELECT DISTINCT ON (product_id) product_id, storage_key, alt_text, colour_name, width, height
                        FROM product_media
                        WHERE kind = 'IMAGE' AND status = 'READY' AND product_id IN (:ids)
                        ORDER BY product_id, position, id
                        """)
                .param("ids", productIds)
                .query(Row.class)
                .list()
                .forEach(row -> images.put(
                        row.productId(),
                        new MediaResponse(
                                MediaKind.IMAGE,
                                row.storageKey(),
                                row.altText(),
                                row.colourName(),
                                row.width(),
                                row.height())));
        return images;
    }

    // --- filter counts -----------------------------------------------------------------------------------

    private record CountRow(
            String dimension, String code, @Nullable Long n) {}

    private record Counts(long total, @Nullable PriceRange priceRange, Map<String, Map<String, Long>> byDimension) {

        Map<String, Long> of(String dimension) {
            return byDimension.getOrDefault(dimension, Map.of());
        }
    }

    /** Total, price range and every option count in one round trip (UNION ALL of one branch per dimension). */
    private Counts counts(ListingFilters filters) {
        var branches = new ArrayList<String>();
        branches.add(
                "SELECT 'total' AS dimension, '' AS code, COUNT(*) AS n FROM listed l WHERE " + filters.where(null));
        var priceWhere = filters.where(Dimension.PRICE);
        branches.add("SELECT 'price', 'min', MIN(l.price_paise)::bigint FROM listed l WHERE " + priceWhere);
        branches.add("SELECT 'price', 'max', MAX(l.price_paise)::bigint FROM listed l WHERE " + priceWhere);
        branches.add(variantCountBranch(COLOUR, "v.colour_family", filters, Dimension.COLOUR));
        branches.add(variantCountBranch(SIZE, "v.size", filters, Dimension.SIZE));

        var facetJoin = "SELECT 'facet:' || f.code, fv.code, COUNT(DISTINCT l.id) FROM listed l"
                + " JOIN product_facet_value pfv ON pfv.product_id = l.id"
                + " JOIN facet_value fv ON fv.id = pfv.facet_value_id"
                + " JOIN facet f ON f.id = fv.facet_id WHERE f.filterable AND ";
        // Facets with no selection share one branch with every filter applied.
        var unselected = new StringBuilder(facetJoin);
        for (var selected : filters.selectedFacets()) {
            unselected.append("f.id <> :").append(selected.idParam()).append(AND);
        }
        branches.add(unselected
                .append(filters.where(null))
                .append(" GROUP BY f.code, fv.code")
                .toString());
        // Each selected facet is counted without its own selection.
        for (var selected : filters.selectedFacets()) {
            branches.add(facetJoin
                    + "f.id = :"
                    + selected.idParam()
                    + AND
                    + filters.where(
                            new Dimension.FacetDimension(selected.facet().code()))
                    + " GROUP BY f.code, fv.code");
        }

        // Reviewed for SQL injection (java:S2077): constant fragments and generated parameter names only, as in
        // items().
        var sql = LISTED + String.join("\nUNION ALL\n", branches);
        var rows = jdbc.sql(sql) // NOSONAR
                .params(filters.params())
                .query(CountRow.class)
                .list();

        return toCounts(rows);
    }

    private static Counts toCounts(List<CountRow> rows) {
        long total = 0;
        Long min = null;
        Long max = null;
        Map<String, Map<String, Long>> byDimension = new HashMap<>();
        for (var row : rows) {
            var n = row.n() == null ? 0L : row.n();
            switch (row.dimension()) {
                case "total" -> total = n;
                case "price" -> {
                    if ("min".equals(row.code())) {
                        min = row.n();
                    } else {
                        max = row.n();
                    }
                }
                default ->
                    byDimension
                            .computeIfAbsent(row.dimension(), d -> new HashMap<>())
                            .put(row.code(), n);
            }
        }
        var priceRange = min == null || max == null ? null : new PriceRange(min.intValue(), max.intValue());
        return new Counts(total, priceRange, byDimension);
    }

    private static String variantCountBranch(
            String dimension, String column, ListingFilters filters, Dimension excluded) {
        var variant = filters.variantConditions(excluded);
        return "SELECT '" + dimension + "', " + column + ", COUNT(DISTINCT l.id) FROM listed l"
                + " JOIN product_variant v ON v.product_id = l.id AND v.active"
                + " WHERE " + column + " IS NOT NULL AND " + filters.where(excluded)
                + (variant.isEmpty() ? "" : AND + variant)
                + " GROUP BY " + column;
    }

    private static List<FilterGroup> filterGroups(
            ListingQuery query, FacetCatalog.Definitions definitions, Counts counts) {
        var groups = new ArrayList<FilterGroup>();

        var colourCounts = counts.of(COLOUR);
        var colours = definitions.colours().stream()
                .filter(c ->
                        colourCounts.containsKey(c.code()) || query.colours().contains(c.code()))
                .map(c -> new FilterOption(
                        c.code(),
                        c.name(),
                        c.hex(),
                        colourCounts.getOrDefault(c.code(), 0L),
                        query.colours().contains(c.code())))
                .toList();
        addIfNotEmpty(groups, new FilterGroup(COLOUR, "Colour", FilterType.COLOUR, colours));

        var sizeCounts = counts.of(SIZE);
        var sizes = new LinkedHashMap<String, Long>(sizeCounts);
        query.sizes().forEach(size -> sizes.putIfAbsent(size, 0L));
        var sizeOptions = sizes.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Long> e) -> sizeRank(e.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .map(e -> new FilterOption(
                        e.getKey(),
                        e.getKey(),
                        null,
                        e.getValue(),
                        query.sizes().contains(e.getKey())))
                .toList();
        addIfNotEmpty(groups, new FilterGroup(SIZE, "Size", FilterType.SIZE, sizeOptions));

        for (var facet : definitions.facets()) {
            if (!facet.filterable()) {
                continue;
            }
            var facetCounts = counts.of("facet:" + facet.code());
            Set<String> selected = query.facets().getOrDefault(facet.code(), Set.of());
            var options = facet.values().stream()
                    .filter(v -> facetCounts.containsKey(v.code()) || selected.contains(v.code()))
                    .map(v -> new FilterOption(
                            v.code(),
                            v.name(),
                            null,
                            facetCounts.getOrDefault(v.code(), 0L),
                            selected.contains(v.code())))
                    .toList();
            addIfNotEmpty(groups, new FilterGroup(facet.code(), facet.name(), FilterType.FACET, options));
        }
        return groups;
    }

    private static void addIfNotEmpty(List<FilterGroup> groups, FilterGroup group) {
        if (!group.options().isEmpty()) {
            groups.add(group);
        }
    }

    private static int sizeRank(String size) {
        var rank = SIZE_ORDER.indexOf(size);
        return rank < 0 ? SIZE_ORDER.size() : rank;
    }
}

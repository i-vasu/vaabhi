package com.vaabhi.catalog;

import com.vaabhi.catalog.ListingFilters.Dimension;
import java.math.BigDecimal;
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
                SELECT p.id, p.slug, p.name, p.subtitle, p.badge, p.popularity, p.published_at, p.category_id,
                       cheapest.price_rupees, cheapest.mrp_rupees, stock.stock_total, stock.in_stock
                FROM product p
                JOIN LATERAL (
                    SELECT v.price_rupees, v.mrp_rupees FROM product_variant v
                    WHERE v.product_id = p.id AND v.active
                    ORDER BY v.price_rupees, v.position
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

    /**
     * Published products from every category whose name, subtitle, category or facet values (Banarasi, wedding…)
     * contain the text; with no text, every product. Replaces the category condition of {@link #LISTED}.
     */
    private static final String MATCHES = """
            (CAST(:q AS varchar) IS NULL
                 OR p.name ILIKE :qLike OR p.subtitle ILIKE :qLike
                 OR EXISTS (SELECT 1 FROM category c WHERE c.id = p.category_id AND c.name ILIKE :qLike)
                 OR EXISTS (SELECT 1 FROM product_facet_value pfv JOIN facet_value fv ON fv.id = pfv.facet_value_id
                            WHERE pfv.product_id = p.id AND fv.name ILIKE :qLike))
            """;

    ProductListingResponse list(CategoryResponse category, ListingQuery query, FacetCatalog.Definitions definitions) {
        var filters = new ListingFilters(category.id(), query, definitions);
        var counts = counts(LISTED, filters);
        var items = items(LISTED, filters, query);
        return new ProductListingResponse(
                category,
                items,
                counts.total(),
                query.page(),
                query.pageSize(),
                query.sort().param(),
                withExtras(filterGroups(query, definitions, counts), extraGroups(filters, counts)),
                counts.priceRange());
    }

    /** Search across the catalogue, with the same filters, counts and sorts as a category listing. */
    SearchResponse search(@Nullable String text, ListingQuery query, FacetCatalog.Definitions definitions) {
        var q = text == null || text.isBlank() ? null : text.trim();
        var filters = new ListingFilters(0, query, definitions);
        filters.params().put("q", q);
        filters.params().put("qLike", q == null ? null : "%" + escapeLike(q) + "%");
        var listed = LISTED.replace("p.category_id = :categoryId", MATCHES);
        var counts = counts(listed, filters);
        var items = items(listed, filters, query);
        return new SearchResponse(
                q,
                items,
                counts.total(),
                query.page(),
                query.pageSize(),
                query.sort().param(),
                withExtras(filterGroups(query, definitions, counts), extraGroups(filters, counts)),
                counts.priceRange());
    }

    /** `ILIKE` treats % and _ as wildcards; a customer typing them means the characters themselves. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // --- items -------------------------------------------------------------------------------------------

    private record ItemRow(
            long id,
            String slug,
            String name,
            @Nullable String subtitle,
            @Nullable String badge,
            BigDecimal priceRupees,
            BigDecimal mrpRupees,
            long stockTotal,
            boolean inStock) {}

    private List<ProductSummary> items(String listed, ListingFilters filters, ListingQuery query) {
        var params = new HashMap<>(filters.params());
        params.put("limit", query.pageSize());
        params.put("offset", query.offset());
        // Reviewed for SQL injection (java:S2077): the SQL text is built only from constants, the ProductSort enum
        // and generated parameter names (ListingFilters); every user-supplied value is a bind parameter.
        var sql = listed
                + "SELECT l.id, l.slug, l.name, l.subtitle, l.badge, l.price_rupees, l.mrp_rupees,"
                + " l.stock_total, l.in_stock FROM listed l WHERE "
                + filters.where(null)
                + " ORDER BY "
                + query.sort().orderBy()
                + " LIMIT :limit OFFSET :offset";
        var rows = jdbc.sql(sql) // NOSONAR
                .params(params)
                .query(ItemRow.class)
                .list();
        return summaries(rows);
    }

    /**
     * Published products with a badge, from every category, most popular first (the home page's Bestsellers).
     */
    // Only constant SQL fragments (and fixed table names / enum-chosen ORDER BY) are joined; values are bound.
    @SuppressWarnings("java:S2077")
    List<ProductSummary> featured(Badge badge, int limit) {
        var rows = jdbc.sql(LISTED.replace("p.category_id = :categoryId", "p.badge = :badge") + """
                        SELECT l.id, l.slug, l.name, l.subtitle, l.badge, l.price_rupees, l.mrp_rupees, l.stock_total,
                               l.in_stock
                        FROM listed l ORDER BY l.popularity DESC, l.published_at DESC, l.id DESC LIMIT :limit
                        """)
                .param("badge", badge.name())
                .param("limit", limit)
                .query(ItemRow.class)
                .list();
        return summaries(rows);
    }

    /**
     * Published products from every category, most recently published first (the storefront's New arrivals).
     * A product keeps its first publish time when it is unpublished and published again.
     */
    // Only constant SQL fragments (and fixed table names / enum-chosen ORDER BY) are joined; values are bound.
    @SuppressWarnings("java:S2077")
    NewArrivalsPage newArrivals(int page, int pageSize) {
        var everyCategory = LISTED.replace("p.category_id = :categoryId", "TRUE");
        var total = jdbc.sql(everyCategory + "SELECT COUNT(*) FROM listed")
                .query(Long.class)
                .single();
        var rows = jdbc.sql(everyCategory + """
                        SELECT l.id, l.slug, l.name, l.subtitle, l.badge, l.price_rupees, l.mrp_rupees, l.stock_total,
                               l.in_stock
                        FROM listed l ORDER BY l.published_at DESC, l.id DESC LIMIT :limit OFFSET :offset
                        """)
                .param("limit", pageSize)
                .param("offset", (page - 1) * pageSize)
                .query(ItemRow.class)
                .list();
        return new NewArrivalsPage(summaries(rows), total, page, pageSize);
    }

    private List<ProductSummary> summaries(List<ItemRow> rows) {
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
                        row.priceRupees(),
                        row.mrpRupees(),
                        Stock.discountPercent(row.priceRupees(), row.mrpRupees()),
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
            String dimension, String code, @Nullable BigDecimal n) {}

    private record Counts(long total, @Nullable PriceRange priceRange, Map<String, Map<String, Long>> byDimension) {

        Map<String, Long> of(String dimension) {
            return byDimension.getOrDefault(dimension, Map.of());
        }
    }

    /** Total, price range and every option count in one round trip (UNION ALL of one branch per dimension). */
    private Counts counts(String listed, ListingFilters filters) {
        var branches = new ArrayList<String>();
        branches.add(
                "SELECT 'total' AS dimension, '' AS code, COUNT(*) AS n FROM listed l WHERE " + filters.where(null));
        var priceWhere = filters.where(Dimension.PRICE);
        branches.add("SELECT 'price', 'min', MIN(l.price_rupees) FROM listed l WHERE " + priceWhere);
        branches.add("SELECT 'price', 'max', MAX(l.price_rupees) FROM listed l WHERE " + priceWhere);
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

        // Category, discount, tag and add-on choices, each counted without its own selection.
        branches.add("SELECT 'x:category', c.slug, COUNT(*) FROM listed l JOIN category c ON c.id = l.category_id"
                + " WHERE " + filters.where(Dimension.CATEGORY) + " GROUP BY c.slug");
        for (var discount : ListingFilters.DISCOUNTS.entrySet()) {
            branches.add("SELECT 'x:discount', '" + discount.getKey() + "', COUNT(*) FROM listed l WHERE "
                    + ListingFilters.discountAtLeast(String.valueOf(discount.getValue())) + AND
                    + filters.where(Dimension.DISCOUNT));
        }
        branches.add("SELECT 'x:tag', LOWER(l.badge), COUNT(*) FROM listed l WHERE l.badge IS NOT NULL AND "
                + filters.where(Dimension.TAG) + " GROUP BY l.badge");
        branches.add("SELECT 'x:addons', 'yes', COUNT(*) FROM listed l WHERE " + ListingFilters.addonExists() + AND
                + filters.where(Dimension.ADDONS));

        // Reviewed for SQL injection (java:S2077): constant fragments and generated parameter names only, as in
        // items(); the discount keys and percentages come from the fixed ListingFilters.DISCOUNTS map.
        var sql = listed + String.join("\nUNION ALL\n", branches);
        var rows = jdbc.sql(sql) // NOSONAR
                .params(filters.params())
                .query(CountRow.class)
                .list();

        return toCounts(rows);
    }

    private static Counts toCounts(List<CountRow> rows) {
        long total = 0;
        BigDecimal min = null;
        BigDecimal max = null;
        Map<String, Map<String, Long>> byDimension = new HashMap<>();
        for (var row : rows) {
            var n = row.n() == null ? 0L : row.n().longValue();
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
        var priceRange = min == null || max == null ? null : new PriceRange(min, max);
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

    private static List<FilterGroup> withExtras(List<FilterGroup> groups, List<FilterGroup> extras) {
        var all = new ArrayList<>(groups);
        all.addAll(extras);
        return all;
    }

    /** Discount choices in the order shown. */
    private static final List<Map.Entry<String, String>> DISCOUNT_NAMES = List.of(
            Map.entry("sale", "On sale"), Map.entry("20", "20% off or more"), Map.entry("40", "40% off or more"));

    private static final List<Map.Entry<String, String>> TAG_NAMES = List.of(
            Map.entry("new", "New"),
            Map.entry("bestseller", "Bestseller"),
            Map.entry("bridal", "Bridal"),
            Map.entry("handloom", "Handloom"));

    /**
     * Category (only where a listing spans several), discount, tags and add-ons: choices that match nothing are
     * left out unless selected, and a group without choices is left out.
     */
    private List<FilterGroup> extraGroups(ListingFilters filters, Counts counts) {
        var groups = new ArrayList<FilterGroup>();

        var categoryCounts = counts.of("x:" + ListingFilters.CATEGORY_CODE);
        var selectedCategories = filters.selected(ListingFilters.CATEGORY_CODE);
        if (categoryCounts.size() > 1 || !selectedCategories.isEmpty()) {
            record CategoryName(String slug, String name) {}
            var categories = jdbc
                    .sql("SELECT slug, name FROM category ORDER BY position, id")
                    .query(CategoryName.class)
                    .list()
                    .stream()
                    .filter(c -> categoryCounts.containsKey(c.slug()) || selectedCategories.contains(c.slug()))
                    .map(c -> new FilterOption(
                            c.slug(),
                            c.name(),
                            null,
                            categoryCounts.getOrDefault(c.slug(), 0L),
                            selectedCategories.contains(c.slug())))
                    .toList();
            addIfNotEmpty(
                    groups, new FilterGroup(ListingFilters.CATEGORY_CODE, "Category", FilterType.FACET, categories));
        }

        groups.add(choices(filters, counts, ListingFilters.DISCOUNT_CODE, "Discount", DISCOUNT_NAMES));
        groups.add(choices(filters, counts, ListingFilters.TAG_CODE, "Tags", TAG_NAMES));
        groups.add(choices(
                filters, counts, ListingFilters.ADDONS_CODE, "Add-ons", List.of(Map.entry("yes", "Offers add-ons"))));
        groups.removeIf(g -> g.options().isEmpty());
        return groups;
    }

    private static FilterGroup choices(
            ListingFilters filters, Counts counts, String code, String name, List<Map.Entry<String, String>> named) {
        var found = counts.of("x:" + code);
        var selected = filters.selected(code);
        var options = named.stream()
                .filter(e -> found.getOrDefault(e.getKey(), 0L) > 0 || selected.contains(e.getKey()))
                .map(e -> new FilterOption(
                        e.getKey(),
                        e.getValue(),
                        null,
                        found.getOrDefault(e.getKey(), 0L),
                        selected.contains(e.getKey())))
                .toList();
        return new FilterGroup(code, name, FilterType.FACET, options);
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

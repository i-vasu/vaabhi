package com.vaabhi.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Builds the SQL WHERE clauses of a listing over the {@code listed} CTE (alias {@code l}). Every user value is a
 * bind parameter; only facet ids from reference data and fixed fragments are concatenated into SQL.
 *
 * <p>Filter counts must ignore the filter being counted (picking "cotton" must not hide "silk"), so each
 * clause can be built with one dimension excluded.
 */
final class ListingFilters {

    /** A dimension to leave out of a clause: price, colour, size, category, discount, tag, add-ons, or a facet. */
    sealed interface Dimension {
        Dimension PRICE = new Fixed("price");
        Dimension COLOUR = new Fixed("colour");
        Dimension SIZE = new Fixed("size");
        Dimension CATEGORY = new Fixed(CATEGORY_CODE);
        Dimension DISCOUNT = new Fixed(DISCOUNT_CODE);
        Dimension TAG = new Fixed(TAG_CODE);
        Dimension ADDONS = new Fixed(ADDONS_CODE);

        record Fixed(String name) implements Dimension {}

        record FacetDimension(String code) implements Dimension {}
    }

    /** Query parameters of the filters that are not facets: {@code category=sarees,lehengas}. */
    static final String CATEGORY_CODE = "category";
    /** {@code discount=sale|20|40}: on sale, or at least that percent off MRP (several: the smallest wins). */
    static final String DISCOUNT_CODE = "discount";
    /** {@code tag=new,bestseller}: the badges the shop sets on products. */
    static final String TAG_CODE = "tag";
    /** {@code addons=yes}: products that offer an add-on (blouse stitching…). */
    static final String ADDONS_CODE = "addons";

    /** Discount choices → the smallest percent off they mean ("sale" = anything off). */
    static final Map<String, Integer> DISCOUNTS = Map.of("sale", 0, "20", 20, "40", 40);

    private static final String ADDON_EXISTS =
            "EXISTS (SELECT 1 FROM product_addon a WHERE a.product_id = l.id AND a.active)";

    /** At least {@code :<param>} percent off; {@code 0} means any discount. */
    static String discountAtLeast(String percentSql) {
        return "(l.mrp_rupees > l.price_rupees AND (l.mrp_rupees - l.price_rupees) * 100 >= " + percentSql
                + " * l.mrp_rupees)";
    }

    static String addonExists() {
        return ADDON_EXISTS;
    }

    private static final String FACET_EXISTS = " AND EXISTS (SELECT 1 FROM product_facet_value pfv"
            + " JOIN facet_value fv ON fv.id = pfv.facet_value_id"
            + " WHERE pfv.product_id = l.id AND fv.facet_id = :";

    record SelectedFacet(FacetCatalog.Facet facet, Set<String> values, String idParam, String valuesParam) {}

    private final ListingQuery query;
    private final List<SelectedFacet> selectedFacets = new ArrayList<>();
    private final Map<String, Object> params = new HashMap<>();
    private final Set<String> categories;
    private final @Nullable Integer discountMin;
    private final Set<String> tags;
    private final boolean addons;

    ListingFilters(long categoryId, ListingQuery query, FacetCatalog.Definitions definitions) {
        this.query = query;
        params.put("categoryId", categoryId);
        var index = 0;
        for (var facet : definitions.facets()) {
            var values = query.facets().get(facet.code());
            if (facet.filterable() && values != null && !values.isEmpty()) {
                var selected = new SelectedFacet(facet, values, "facetId" + index, "facetValues" + index);
                params.put(selected.idParam(), facet.id());
                params.put(selected.valuesParam(), List.copyOf(values));
                selectedFacets.add(selected);
                index++;
            }
        }
        if (!query.colours().isEmpty()) {
            params.put("colours", List.copyOf(query.colours()));
        }
        if (!query.sizes().isEmpty()) {
            params.put("sizes", List.copyOf(query.sizes()));
        }
        if (query.priceMinRupees() != null) {
            params.put("priceMin", query.priceMinRupees());
        }
        if (query.priceMaxRupees() != null) {
            params.put("priceMax", query.priceMaxRupees());
        }

        categories = query.facets().getOrDefault(CATEGORY_CODE, Set.of());
        if (!categories.isEmpty()) {
            params.put("categorySlugs", List.copyOf(categories));
        }
        discountMin = query.facets().getOrDefault(DISCOUNT_CODE, Set.of()).stream()
                .map(DISCOUNTS::get)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compare)
                .orElse(null);
        if (discountMin != null) {
            params.put("discountMin", discountMin);
        }
        tags = query.facets().getOrDefault(TAG_CODE, Set.of()).stream()
                .map(t -> t.toUpperCase(java.util.Locale.ROOT))
                .filter(t -> java.util.Arrays.stream(Badge.values()).anyMatch(b -> b.name().equals(t)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!tags.isEmpty()) {
            params.put("tags", List.copyOf(tags));
        }
        addons = query.facets().getOrDefault(ADDONS_CODE, Set.of()).contains("yes");
    }

    /** Selected values of a non-facet filter, for marking options selected. */
    Set<String> selected(String code) {
        return switch (code) {
            case CATEGORY_CODE -> categories;
            case DISCOUNT_CODE -> query.facets().getOrDefault(DISCOUNT_CODE, Set.of());
            case TAG_CODE -> query.facets().getOrDefault(TAG_CODE, Set.of());
            case ADDONS_CODE -> addons ? Set.of("yes") : Set.of();
            default -> Set.of();
        };
    }

    Map<String, Object> params() {
        return params;
    }

    List<SelectedFacet> selectedFacets() {
        return selectedFacets;
    }

    /** All filters, optionally without one dimension; always a valid boolean expression. */
    String where(@Nullable Dimension excluded) {
        var sql = new StringBuilder("TRUE");
        for (var selected : selectedFacets) {
            if (!new Dimension.FacetDimension(selected.facet().code()).equals(excluded)) {
                sql.append(FACET_EXISTS)
                        .append(selected.idParam())
                        .append(" AND fv.code IN (:")
                        .append(selected.valuesParam())
                        .append("))");
            }
        }
        var variant = variantConditions(excluded);
        if (!variant.isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM product_variant v WHERE v.product_id = l.id AND v.active AND ")
                    .append(variant)
                    .append(")");
        }
        if (!Dimension.PRICE.equals(excluded)) {
            if (query.priceMinRupees() != null) {
                sql.append(" AND l.price_rupees >= :priceMin");
            }
            if (query.priceMaxRupees() != null) {
                sql.append(" AND l.price_rupees <= :priceMax");
            }
        }
        if (!categories.isEmpty() && !Dimension.CATEGORY.equals(excluded)) {
            sql.append(" AND l.category_id IN (SELECT c.id FROM category c WHERE c.slug IN (:categorySlugs))");
        }
        if (discountMin != null && !Dimension.DISCOUNT.equals(excluded)) {
            sql.append(" AND ").append(discountAtLeast(":discountMin"));
        }
        if (!tags.isEmpty() && !Dimension.TAG.equals(excluded)) {
            sql.append(" AND l.badge IN (:tags)");
        }
        if (addons && !Dimension.ADDONS.equals(excluded)) {
            sql.append(" AND ").append(ADDON_EXISTS);
        }
        return sql.toString();
    }

    /**
     * Conditions on a single variant {@code v} (colour, size, in stock), so "red, size M, in stock" means one
     * variant that is all three. Empty when none apply.
     */
    String variantConditions(@Nullable Dimension excluded) {
        var conditions = new ArrayList<String>();
        if (!query.colours().isEmpty() && !Dimension.COLOUR.equals(excluded)) {
            conditions.add("v.colour_family IN (:colours)");
        }
        if (!query.sizes().isEmpty() && !Dimension.SIZE.equals(excluded)) {
            conditions.add("v.size IN (:sizes)");
        }
        if (query.inStock()) {
            conditions.add("v.stock_qty > 0");
        }
        return String.join(" AND ", conditions);
    }
}

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

    /** A dimension to leave out of a clause: price, colour, size, or a facet by code. */
    sealed interface Dimension {
        Dimension PRICE = new Fixed("price");
        Dimension COLOUR = new Fixed("colour");
        Dimension SIZE = new Fixed("size");

        record Fixed(String name) implements Dimension {}

        record FacetDimension(String code) implements Dimension {}
    }

    private static final String FACET_EXISTS = " AND EXISTS (SELECT 1 FROM product_facet_value pfv"
            + " JOIN facet_value fv ON fv.id = pfv.facet_value_id"
            + " WHERE pfv.product_id = l.id AND fv.facet_id = :";

    record SelectedFacet(FacetCatalog.Facet facet, Set<String> values, String idParam, String valuesParam) {}

    private final ListingQuery query;
    private final List<SelectedFacet> selectedFacets = new ArrayList<>();
    private final Map<String, Object> params = new HashMap<>();

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
        if (query.priceMinPaise() != null) {
            params.put("priceMin", query.priceMinPaise());
        }
        if (query.priceMaxPaise() != null) {
            params.put("priceMax", query.priceMaxPaise());
        }
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
            if (query.priceMinPaise() != null) {
                sql.append(" AND l.price_paise >= :priceMin");
            }
            if (query.priceMaxPaise() != null) {
                sql.append(" AND l.price_paise <= :priceMax");
            }
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

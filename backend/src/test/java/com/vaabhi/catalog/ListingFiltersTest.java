package com.vaabhi.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.catalog.ListingFilters.Dimension;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListingFiltersTest {

    private static final FacetCatalog.Definitions DEFINITIONS = new FacetCatalog.Definitions(
            List.of(
                    new FacetCatalog.Facet(
                            11, "fabric", "Fabric", true, List.of(new FacetCatalog.Value("silk", "Silk"))),
                    new FacetCatalog.Facet(
                            12, "occasion", "Occasion", true, List.of(new FacetCatalog.Value("wedding", "Wedding"))),
                    new FacetCatalog.Facet(13, "internal", "Internal", false, List.of())),
            List.of(new FacetCatalog.ColourFamily("red", "Red", "#B3261E")));

    private static ListingQuery query(
            Map<String, Set<String>> facets, Set<String> colours, Set<String> sizes, boolean inStock) {
        return new ListingQuery("sarees", facets, colours, sizes, inStock, 1000, 5000, ProductSort.POPULAR, 2, 24);
    }

    @Test
    void noFiltersIsAlwaysTrueWithPrice() {
        var filters = new ListingFilters(5, query(Map.of(), Set.of(), Set.of(), false), DEFINITIONS);

        assertThat(filters.where(null)).isEqualTo("TRUE AND l.price_paise >= :priceMin AND l.price_paise <= :priceMax");
        assertThat(filters.where(Dimension.PRICE)).isEqualTo("TRUE");
        assertThat(filters.params()).containsEntry("categoryId", 5L).containsEntry("priceMin", 1000);
    }

    @Test
    void selectedFacetsBecomeBoundParametersAndCanBeExcluded() {
        var filters = new ListingFilters(
                5,
                query(
                        Map.of("fabric", Set.of("silk"), "unknown", Set.of("x"), "internal", Set.of("y")),
                        Set.of(),
                        Set.of(),
                        false),
                DEFINITIONS);

        assertThat(filters.selectedFacets())
                .singleElement()
                .satisfies(s -> assertThat(s.facet().code()).isEqualTo("fabric"));
        assertThat(filters.params()).containsEntry("facetId0", 11L).containsEntry("facetValues0", List.of("silk"));
        assertThat(filters.where(null)).contains("fv.facet_id = :facetId0 AND fv.code IN (:facetValues0)");
        assertThat(filters.where(new Dimension.FacetDimension("fabric"))).doesNotContain("facetId0");
        assertThat(filters.where(null)).doesNotContain("unknown").doesNotContain("internal");
    }

    @Test
    void colourSizeAndStockApplyToTheSameVariant() {
        var filters = new ListingFilters(5, query(Map.of(), Set.of("red"), Set.of("M"), true), DEFINITIONS);

        assertThat(filters.variantConditions(null))
                .isEqualTo("v.colour_family IN (:colours) AND v.size IN (:sizes) AND v.stock_qty > 0");
        assertThat(filters.variantConditions(Dimension.COLOUR)).isEqualTo("v.size IN (:sizes) AND v.stock_qty > 0");
        assertThat(filters.variantConditions(Dimension.SIZE))
                .isEqualTo("v.colour_family IN (:colours) AND v.stock_qty > 0");
        assertThat(filters.where(null)).containsOnlyOnce("FROM product_variant v");
    }

    @Test
    void pagesAreOneBased() {
        assertThat(query(Map.of(), Set.of(), Set.of(), false).offset()).isEqualTo(24);
    }
}

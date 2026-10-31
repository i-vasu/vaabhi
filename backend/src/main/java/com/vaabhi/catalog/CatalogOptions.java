package com.vaabhi.catalog;

import java.util.List;

/** Reference data for the admin product form. */
public record CatalogOptions(
        List<CategoryResponse> categories,
        List<FacetOption> facets,
        List<ColourOption> colourFamilies,
        List<String> sizes) {

    public record FacetOption(String code, String name, List<AttributeResponse.AttributeValue> values) {}

    public record ColourOption(String code, String name, String hex) {}
}

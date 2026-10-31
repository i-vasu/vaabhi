package com.vaabhi.catalog;

import java.util.List;

/** A facet and the product's values for it, e.g. Fabric: Silk. */
public record AttributeResponse(String code, String name, List<AttributeValue> values) {

    public record AttributeValue(String code, String name) {}
}

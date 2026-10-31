package com.vaabhi.catalog;

/** Draft → Published (visible in the store) → Archived. Only published products are ever shown to shoppers. */
public enum ProductStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}

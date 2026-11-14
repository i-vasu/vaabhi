package com.vaabhi.catalog;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tells other modules which tax class a product's category carries. */
@Service
public class CatalogTax {

    private final JdbcClient jdbc;

    CatalogTax(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The tax class of the product's category; null when the product is unknown or its category has none. */
    @Transactional(readOnly = true)
    public @Nullable Long classFor(String productSlug) {
        var classes = jdbc.sql("""
                        SELECT c.tax_class_id FROM product p
                        JOIN category c ON c.id = p.category_id
                        WHERE p.slug = :slug
                        """).param("slug", productSlug).query(Long.class).list();
        return classes.isEmpty() ? null : classes.get(0);
    }
}

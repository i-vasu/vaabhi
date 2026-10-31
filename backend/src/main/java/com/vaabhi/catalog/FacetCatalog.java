package com.vaabhi.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Facet and colour-family reference data, cached: it changes only when the admin edits it. */
@Component
class FacetCatalog {

    static final String CACHE = "facets";

    record Value(String code, String name) {}

    record Facet(long id, String code, String name, boolean filterable, List<Value> values) {

        Optional<Value> value(String code) {
            return values.stream().filter(v -> v.code().equals(code)).findFirst();
        }
    }

    record ColourFamily(String code, String name, String hex) {}

    record Definitions(List<Facet> facets, List<ColourFamily> colours) {

        Optional<Facet> facet(String code) {
            return facets.stream().filter(f -> f.code().equals(code)).findFirst();
        }
    }

    private final JdbcClient jdbc;

    FacetCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Cacheable(CACHE)
    public Definitions definitions() {
        record Row(long id, String code, String name, boolean filterable, String valueCode, String valueName) {}

        var rows = jdbc.sql("""
                        SELECT f.id, f.code, f.name, f.filterable, fv.code AS value_code, fv.name AS value_name
                        FROM facet f
                        JOIN facet_value fv ON fv.facet_id = f.id
                        ORDER BY f.position, f.id, fv.position, fv.id
                        """).query(Row.class).list();

        Map<Long, Facet> facets = new LinkedHashMap<>();
        for (var row : rows) {
            facets.computeIfAbsent(
                            row.id(), id -> new Facet(id, row.code(), row.name(), row.filterable(), new ArrayList<>()))
                    .values()
                    .add(new Value(row.valueCode(), row.valueName()));
        }

        var colours = jdbc.sql("SELECT code, name, hex FROM colour_family ORDER BY position, code")
                .query(ColourFamily.class)
                .list();

        return new Definitions(
                facets.values().stream()
                        .map(f -> new Facet(f.id(), f.code(), f.name(), f.filterable(), List.copyOf(f.values())))
                        .toList(),
                colours);
    }
}

package com.vaabhi.accounts;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** The column mapping last used for a kind of file, so the next import starts from it. */
@Service
public class ImportProfileService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    ImportProfileService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The newest mapping saved for {@code kind}, or null when nothing was imported yet. */
    @Transactional(readOnly = true)
    public @Nullable ColumnMapping latest(String kind) {
        return jdbc.sql("""
                        SELECT mapping::text FROM import_profile
                        WHERE kind = :kind
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """)
                .param("kind", kind)
                .query(String.class)
                .optional()
                .map(json -> JSON.readValue(json, ColumnMapping.class))
                .orElse(null);
    }

    /** Saves the mapping as the newest one for {@code kind}. */
    @Transactional
    public void save(String kind, ColumnMapping mapping) {
        jdbc.sql("INSERT INTO import_profile (kind, mapping) VALUES (:kind, CAST(:mapping AS jsonb))")
                .param("kind", kind)
                .param("mapping", JSON.writeValueAsString(mapping))
                .update();
    }
}

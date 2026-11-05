package com.vaabhi.settings;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** One JSON document per settings section. Every statement is static; all values are bind parameters. */
@Component
class SettingsStore {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    SettingsStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    <T> Optional<T> find(String section, Class<T> type) {
        return jdbc.sql("SELECT value::text FROM shop_setting WHERE section = :section")
                .param("section", section)
                .query(String.class)
                .optional()
                .map(value -> json.readValue(value, type));
    }

    void save(String section, Object value, @Nullable String updatedBy) {
        jdbc.sql("""
                        INSERT INTO shop_setting (section, value, updated_by) VALUES (:section, CAST(:value AS jsonb), :by)
                        ON CONFLICT (section) DO UPDATE
                        SET value = EXCLUDED.value, updated_at = now(), updated_by = EXCLUDED.updated_by
                        """)
                .param("section", section)
                .param("value", json.writeValueAsString(value))
                .param("by", updatedBy)
                .update();
    }
}

package com.vaabhi.purchases;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Whether the books hold an entry of one source and step. The journal is read by SQL: purchases keeps no copy of it
 * and does not post to it.
 */
@Component
public class BooksPostedCheck {

    private final JdbcClient jdbc;

    BooksPostedCheck(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean posted(String sourceType, long sourceId, String step) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM journal_entry
                                       WHERE source_type = :type AND source_id = :id AND source_step = :step)
                        """)
                .param("type", sourceType)
                .param("id", sourceId)
                .param("step", step)
                .query(Boolean.class)
                .single();
    }
}

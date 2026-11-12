package com.vaabhi.accounts;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records, lists and clears the sources the sweeper could not post. */
@Service
public class PostingProblemService {

    private static final int MAX_MESSAGE = 500;

    private final JdbcClient jdbc;

    PostingProblemService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String sourceType, long sourceId, String sourceStep, String message) {
        var text = message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE);
        jdbc.sql("""
                        INSERT INTO posting_problem (source_type, source_id, source_step, message)
                        VALUES (:type, :id, :step, :message)
                        ON CONFLICT (source_type, source_id, source_step)
                        DO UPDATE SET message = EXCLUDED.message, last_seen = now()
                        """)
                .param("type", sourceType)
                .param("id", sourceId)
                .param("step", sourceStep)
                .param("message", text)
                .update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(String sourceType, long sourceId, String sourceStep) {
        jdbc.sql("DELETE FROM posting_problem WHERE source_type = :type AND source_id = :id AND source_step = :step")
                .param("type", sourceType)
                .param("id", sourceId)
                .param("step", sourceStep)
                .update();
    }

    /** Clears every problem of one source type (the daily check re-records the ones still true). */
    @Transactional
    public void clearType(String sourceType) {
        jdbc.sql("DELETE FROM posting_problem WHERE source_type = :type")
                .param("type", sourceType)
                .update();
    }

    /** Source ids with a recorded problem for one type and step. */
    @Transactional(readOnly = true)
    public List<Long> idsFor(String sourceType, String sourceStep) {
        return jdbc.sql("SELECT source_id FROM posting_problem WHERE source_type = :type AND source_step = :step")
                .param("type", sourceType)
                .param("step", sourceStep)
                .query(Long.class)
                .list();
    }

    @Transactional(readOnly = true)
    public List<PostingProblem> list() {
        return jdbc.sql("""
                        SELECT source_type, source_id, source_step, message, first_seen, last_seen
                        FROM posting_problem ORDER BY first_seen, source_type, source_id, source_step
                        """).query(PostingProblem.class).list();
    }

    @Transactional(readOnly = true)
    public int count() {
        return jdbc.sql("SELECT COUNT(*) FROM posting_problem")
                .query(Integer.class)
                .single();
    }
}

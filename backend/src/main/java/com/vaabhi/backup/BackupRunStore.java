package com.vaabhi.backup;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Rows in {@code backup_run}: one per nightly try, monthly check or restore drill. */
@Component
class BackupRunStore {

    private final JdbcClient jdbc;

    BackupRunStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Starts a run; {@code -1} when one of the same kind is still running. */
    long start(String kind, @Nullable String actor) {
        try {
            return jdbc.sql("""
                            INSERT INTO backup_run (kind, status, created_by) VALUES (:kind, 'RUNNING', :actor)
                            RETURNING id
                            """)
                    .param("kind", kind)
                    .param("actor", actor)
                    .query(Long.class)
                    .single();
        } catch (DuplicateKeyException e) {
            return -1;
        }
    }

    void ok(
            long id,
            long size,
            String sha,
            String key,
            @Nullable String monthlyKey,
            int attachments,
            @Nullable String notes) {
        jdbc.sql("""
                        UPDATE backup_run
                        SET status = 'OK', finished_at = now(), size_bytes = :size, sha256 = :sha,
                            object_key = :key, monthly_key = :monthly, attachments_copied = :attachments,
                            notes = :notes
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("size", size)
                .param("sha", sha)
                .param("key", key)
                .param("monthly", monthlyKey)
                .param("attachments", attachments)
                .param("notes", notes)
                .update();
    }

    void fail(long id, String message) {
        jdbc.sql("UPDATE backup_run SET status = 'FAILED', finished_at = now(), message = :message WHERE id = :id")
                .param("id", id)
                .param("message", message)
                .update();
    }

    /** A RUNNING row left behind by a crash becomes FAILED "Interrupted" once it is older than {@code olderThan}. */
    int expireStale(Duration olderThan) {
        return jdbc.sql("""
                        UPDATE backup_run
                        SET status = 'FAILED', finished_at = now(), message = 'Interrupted'
                        WHERE status = 'RUNNING' AND started_at < :cutoff
                        """)
                .param("cutoff", Timestamp.from(Instant.now().minus(olderThan)))
                .update();
    }

    /** Whether an OK database run started at or after {@code since}. */
    boolean hasOkSince(Instant since) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM backup_run
                                       WHERE kind = 'DB' AND status = 'OK' AND started_at >= :since)
                        """)
                .param("since", Timestamp.from(since))
                .query(Boolean.class)
                .single();
    }
}

package com.vaabhi.customer;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * SQL for one-time codes. Rows are keyed on the {@code phone} column, which holds either the number itself
 * (sign-in) or a purpose-prefixed key (phone change), so a code for one purpose can never verify the other.
 */
@Component
class OtpStore {

    private static final String PHONE = "phone";

    private final JdbcClient jdbc;

    OtpStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Live(long id, String codeHash, int attempts) {}

    /** Codes sent under this key since {@code since}, and whether one was sent after {@code recentSince}. */
    record Recent(long count, boolean tooSoon) {}

    Recent recentFor(String key, OffsetDateTime since, OffsetDateTime recentSince) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS count, COALESCE(BOOL_OR(created_at > :recentSince), FALSE) AS too_soon
                        FROM otp_challenge WHERE phone = :phone AND created_at > :since
                        """)
                .param(PHONE, key)
                .param("since", since)
                .param("recentSince", recentSince)
                .query(Recent.class)
                .single();
    }

    long countSince(OffsetDateTime since) {
        return jdbc.sql("SELECT COUNT(*) FROM otp_challenge WHERE created_at > :since")
                .param("since", since)
                .query(Long.class)
                .single();
    }

    /** Stores a new code; any earlier unused code for the key stops working. */
    void replace(String key, String codeHash, OffsetDateTime now, OffsetDateTime expiresAt) {
        jdbc.sql("UPDATE otp_challenge SET consumed_at = :now WHERE phone = :phone AND consumed_at IS NULL")
                .param(PHONE, key)
                .param("now", now)
                .update();
        jdbc.sql("""
                        INSERT INTO otp_challenge (phone, code_hash, created_at, expires_at)
                        VALUES (:phone, :codeHash, :now, :expiresAt)
                        """)
                .param(PHONE, key)
                .param("codeHash", codeHash)
                .param("now", now)
                .param("expiresAt", expiresAt)
                .update();
    }

    /** The current unexpired code for the key, locked until the transaction ends. */
    Optional<Live> lockLive(String key, OffsetDateTime now) {
        return jdbc.sql("""
                        SELECT id, code_hash, attempts FROM otp_challenge
                        WHERE phone = :phone AND consumed_at IS NULL AND expires_at > :now
                        ORDER BY created_at DESC LIMIT 1
                        FOR UPDATE
                        """)
                .param(PHONE, key)
                .param("now", now)
                .query(Live.class)
                .optional();
    }

    void countAttempt(long id) {
        jdbc.sql("UPDATE otp_challenge SET attempts = attempts + 1 WHERE id = :id")
                .param("id", id)
                .update();
    }

    void consume(long id, OffsetDateTime now) {
        jdbc.sql("UPDATE otp_challenge SET consumed_at = :now WHERE id = :id")
                .param("id", id)
                .param("now", now)
                .update();
    }

    int deleteOlderThan(OffsetDateTime before) {
        return jdbc.sql("DELETE FROM otp_challenge WHERE created_at < :before")
                .param("before", before)
                .update();
    }
}

package com.vaabhi.customer;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** SQL for one-time codes. */
@Component
class OtpStore {

    private static final String PHONE = "phone";

    private final JdbcClient jdbc;

    OtpStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Live(long id, String codeHash, int attempts) {}

    /** Codes sent to this number since {@code since}, and whether one was sent after {@code recentSince}. */
    record Recent(long count, boolean tooSoon) {}

    Recent recentFor(String phone, OffsetDateTime since, OffsetDateTime recentSince) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS count, COALESCE(BOOL_OR(created_at > :recentSince), FALSE) AS too_soon
                        FROM otp_challenge WHERE phone = :phone AND created_at > :since
                        """)
                .param(PHONE, phone)
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

    /** Stores a new code; any earlier unused code for the number stops working. */
    void replace(String phone, String codeHash, OffsetDateTime now, OffsetDateTime expiresAt) {
        jdbc.sql("UPDATE otp_challenge SET consumed_at = :now WHERE phone = :phone AND consumed_at IS NULL")
                .param(PHONE, phone)
                .param("now", now)
                .update();
        jdbc.sql("""
                        INSERT INTO otp_challenge (phone, code_hash, created_at, expires_at)
                        VALUES (:phone, :codeHash, :now, :expiresAt)
                        """)
                .param(PHONE, phone)
                .param("codeHash", codeHash)
                .param("now", now)
                .param("expiresAt", expiresAt)
                .update();
    }

    /** The current unexpired code for the number, locked until the transaction ends. */
    Optional<Live> lockLive(String phone, OffsetDateTime now) {
        return jdbc.sql("""
                        SELECT id, code_hash, attempts FROM otp_challenge
                        WHERE phone = :phone AND consumed_at IS NULL AND expires_at > :now
                        ORDER BY created_at DESC LIMIT 1
                        FOR UPDATE
                        """)
                .param(PHONE, phone)
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

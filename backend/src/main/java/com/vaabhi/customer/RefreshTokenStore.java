package com.vaabhi.customer;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** SQL for refresh tokens (stored as SHA-256 hashes). */
@Component
class RefreshTokenStore {

    private static final String HASH = "hash";
    private static final String NOW = "now";

    private final JdbcClient jdbc;

    RefreshTokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param revoked ended: rotated, logged out or revoked
     * @param recentlyRotated replaced by a newer token within the reuse grace period (a parallel refresh, not
     *     theft); never true for logged-out or revoked tokens
     */
    record Stored(long id, long customerId, boolean revoked, boolean recentlyRotated, boolean expired) {}

    void insert(long customerId, String hash, OffsetDateTime now, OffsetDateTime expiresAt) {
        jdbc.sql("""
                        INSERT INTO refresh_token (customer_id, token_hash, created_at, expires_at)
                        VALUES (:customerId, :hash, :now, :expiresAt)
                        """)
                .param("customerId", customerId)
                .param(HASH, hash)
                .param(NOW, now)
                .param("expiresAt", expiresAt)
                .update();
    }

    Optional<Stored> lock(String hash, OffsetDateTime now, OffsetDateTime graceSince) {
        return jdbc.sql("""
                        SELECT id, customer_id, revoked_at IS NOT NULL AS revoked,
                               COALESCE(rotated_at > :graceSince, FALSE) AS recently_rotated,
                               expires_at <= :now AS expired
                        FROM refresh_token WHERE token_hash = :hash
                        FOR UPDATE
                        """)
                .param(HASH, hash)
                .param(NOW, now)
                .param("graceSince", graceSince)
                .query(Stored.class)
                .optional();
    }

    /** Ends a token because a newer one replaced it. */
    void rotate(long id, OffsetDateTime now) {
        jdbc.sql("""
                        UPDATE refresh_token SET revoked_at = :now, rotated_at = :now
                        WHERE id = :id AND revoked_at IS NULL
                        """).param("id", id).param(NOW, now).update();
    }

    int revokeByHash(String hash, OffsetDateTime now) {
        return jdbc.sql("UPDATE refresh_token SET revoked_at = :now WHERE token_hash = :hash AND revoked_at IS NULL")
                .param(HASH, hash)
                .param(NOW, now)
                .update();
    }

    /** Ends every session of the customer, including the grace period of recently rotated tokens. */
    int revokeAll(long customerId, OffsetDateTime now) {
        return jdbc.sql("""
                        UPDATE refresh_token SET revoked_at = COALESCE(revoked_at, :now), rotated_at = NULL
                        WHERE customer_id = :customerId AND (revoked_at IS NULL OR rotated_at IS NOT NULL)
                        """).param("customerId", customerId).param(NOW, now).update();
    }

    int deleteExpired(OffsetDateTime now) {
        return jdbc.sql("DELETE FROM refresh_token WHERE expires_at < :now")
                .param(NOW, now)
                .update();
    }
}

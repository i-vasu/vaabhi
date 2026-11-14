package com.vaabhi.shared.database;

import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Tells the database who is acting. When a staff member is signed in, each transaction starts with
 * {@code vaabhi.actor} set to their name (transaction-local), so the audit triggers can record it. Scheduled jobs and
 * storefront customers set nothing.
 */
@Component
class ActorTransactionListener implements TransactionExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ActorTransactionListener.class);

    private static final String STAFF_PREFIX = "staff:";

    private final JdbcClient jdbc;

    ActorTransactionListener(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public void afterBegin(TransactionExecution transaction, @Nullable Throwable beginFailure) {
        if (beginFailure != null) {
            return;
        }
        var actor = currentStaffName();
        if (actor == null) {
            return;
        }
        try {
            jdbc.sql("SELECT set_config('vaabhi.actor', :actor, true)")
                    .param("actor", actor)
                    .query(String.class)
                    .single();
        } catch (RuntimeException e) {
            // A failed statement aborts the Postgres transaction; end it cleanly (and visibly) instead of
            // throwing from a listener Spring does not guard.
            log.error("Could not record the acting staff member; rolling the transaction back", e);
            transaction.setRollbackOnly();
        }
    }

    private static @Nullable String currentStaffName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return null;
        }
        var jwt = token.getToken();
        var subject = jwt.getSubject();
        if (subject == null || !subject.startsWith(STAFF_PREFIX)) {
            return null;
        }
        var name = jwt.getClaimAsString("name");
        return name == null || name.isBlank() ? null : name;
    }
}

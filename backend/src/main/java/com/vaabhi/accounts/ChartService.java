package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The chart of accounts. */
@Service
public class ChartService {

    private static final String SELECT = """
            SELECT a.id, a.code, a.name, a.type, a.schedule_line, a.parent_id, a.system_key, a.active,
                   COALESCE(SUM(l.debit_rupees), 0) AS debits, COALESCE(SUM(l.credit_rupees), 0) AS credits
            FROM account a LEFT JOIN journal_line l ON l.account_id = a.id
            """;
    private static final String GROUP = " GROUP BY a.id ";

    private final JdbcClient jdbc;

    ChartService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record AccountRow(
            long id,
            String code,
            String name,
            AccountType type,
            ScheduleLine scheduleLine,
            @Nullable Long parentId,
            @Nullable String systemKey,
            boolean active,
            BigDecimal debits,
            BigDecimal credits) {

        Account toAccount() {
            var balance = type.debitNormal() ? debits.subtract(credits) : credits.subtract(debits);
            return new Account(id, code, name, type, scheduleLine, parentId, systemKey, active, Rupees.of(balance));
        }
    }

    @Transactional(readOnly = true)
    public List<Account> list() {
        return jdbc.sql(SELECT + GROUP + "ORDER BY a.code").query(AccountRow.class).list().stream()
                .map(AccountRow::toAccount)
                .toList();
    }

    @Transactional(readOnly = true)
    public Account get(long id) {
        return jdbc.sql(SELECT + " WHERE a.id = :id" + GROUP)
                .param("id", id)
                .query(AccountRow.class)
                .optional()
                .map(AccountRow::toAccount)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional
    public Account create(AccountForm form) {
        checkParent(form.parentId());
        checkCodeFree(form.code(), -1);
        var line = lineFor(form.type(), form.scheduleLine(), null);
        long id = jdbc.sql("""
                        INSERT INTO account (code, name, type, parent_id, schedule_line)
                        VALUES (:code, :name, :type, :parentId, :line) RETURNING id
                        """)
                .param("code", form.code())
                .param("name", form.name().trim())
                .param("type", form.type().name())
                .param("parentId", form.parentId())
                .param("line", line.name())
                .query(Long.class)
                .single();
        return get(id);
    }

    @Transactional
    public Account update(long id, AccountForm form) {
        var current = get(id);
        if (current.systemKey() != null) {
            if (Boolean.FALSE.equals(form.active())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "System accounts can't be turned off");
            }
            var line = lineFor(current.type(), form.scheduleLine(), current.scheduleLine());
            jdbc.sql("UPDATE account SET name = :name, schedule_line = :line WHERE id = :id")
                    .param("name", form.name().trim())
                    .param("line", line.name())
                    .param("id", id)
                    .update();
            return get(id);
        }
        if (form.parentId() != null && form.parentId() == id) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account can't be its own parent");
        }
        checkParent(form.parentId());
        checkCodeFree(form.code(), id);
        if (Boolean.FALSE.equals(form.active()) && current.active()) {
            var mode = jdbc.sql("""
                            SELECT key FROM books_setting, jsonb_each_text(mode_accounts)
                            WHERE id = 1 AND value::bigint = :id ORDER BY key LIMIT 1
                            """).param("id", id).query(String.class).optional();
            if (mode.isPresent()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This account is used for " + mode.get() + " payments; pick another account there first");
            }
        }
        if (form.type() != current.type()) {
            boolean posted = jdbc.sql("SELECT EXISTS (SELECT 1 FROM journal_line WHERE account_id = :id)")
                    .param("id", id)
                    .query(Boolean.class)
                    .single();
            if (posted) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with postings can't change type");
            }
        }
        var keptLine = form.type() == current.type() ? current.scheduleLine() : null;
        var line = lineFor(form.type(), form.scheduleLine(), keptLine);
        jdbc.sql("""
                        UPDATE account SET code = :code, name = :name, type = :type, parent_id = :parentId,
                                           active = :active, schedule_line = :line
                        WHERE id = :id
                        """)
                .param("code", form.code())
                .param("name", form.name().trim())
                .param("type", form.type().name())
                .param("parentId", form.parentId())
                .param("active", form.active() == null ? current.active() : form.active())
                .param("line", line.name())
                .param("id", id)
                .update();
        return get(id);
    }

    @Transactional(readOnly = true)
    public long idOf(String systemKey) {
        return jdbc.sql("SELECT id FROM account WHERE system_key = :key")
                .param("key", systemKey)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("No account with system key " + systemKey));
    }

    private static ScheduleLine lineFor(
            AccountType type, @Nullable ScheduleLine requested, @Nullable ScheduleLine current) {
        var line = requested != null ? requested : current;
        if (line == null) {
            line = ScheduleLine.defaultFor(type);
        }
        if (!line.fits(type)) {
            var article = type == AccountType.LIABILITY ? "a" : "an";
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    line.label() + " is not " + article + " " + type.name().toLowerCase(Locale.ROOT) + " line");
        }
        return line;
    }

    private void checkParent(@Nullable Long parentId) {
        if (parentId == null) {
            return;
        }
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM account WHERE id = :id)")
                .param("id", parentId)
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent account not found");
        }
    }

    private void checkCodeFree(String code, long exceptId) {
        boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM account WHERE code = :code AND id <> :id)")
                .param("code", code)
                .param("id", exceptId)
                .query(Boolean.class)
                .single();
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Code " + code + " is already used");
        }
    }
}

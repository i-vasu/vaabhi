package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.util.List;
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
            SELECT a.id, a.code, a.name, a.type, a.parent_id, a.system_key, a.active,
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
            @Nullable Long parentId,
            @Nullable String systemKey,
            boolean active,
            BigDecimal debits,
            BigDecimal credits) {

        Account toAccount() {
            var balance = type.debitNormal() ? debits.subtract(credits) : credits.subtract(debits);
            return new Account(id, code, name, type, parentId, systemKey, active, Rupees.of(balance));
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
        long id = jdbc.sql("""
                        INSERT INTO account (code, name, type, parent_id)
                        VALUES (:code, :name, :type, :parentId) RETURNING id
                        """)
                .param("code", form.code())
                .param("name", form.name().trim())
                .param("type", form.type().name())
                .param("parentId", form.parentId())
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
            jdbc.sql("UPDATE account SET name = :name WHERE id = :id")
                    .param("name", form.name().trim())
                    .param("id", id)
                    .update();
            return get(id);
        }
        if (form.parentId() != null && form.parentId() == id) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account can't be its own parent");
        }
        checkParent(form.parentId());
        checkCodeFree(form.code(), id);
        jdbc.sql("""
                        UPDATE account SET code = :code, name = :name, type = :type, parent_id = :parentId,
                                           active = :active
                        WHERE id = :id
                        """)
                .param("code", form.code())
                .param("name", form.name().trim())
                .param("type", form.type().name())
                .param("parentId", form.parentId())
                .param("active", form.active() == null ? current.active() : form.active())
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

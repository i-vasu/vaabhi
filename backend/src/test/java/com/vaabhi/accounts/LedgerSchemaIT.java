package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The ledger's database rules: balance, immutability, month locks, audit log, seed data. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class LedgerSchemaIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    private long account(String systemKey) {
        return jdbc.sql("SELECT id FROM account WHERE system_key = :key")
                .param("key", systemKey)
                .query(Long.class)
                .single();
    }

    private long entry(String date) {
        return jdbc.sql("""
                        INSERT INTO journal_entry (number, entry_date, source_type, source_step, narration)
                        VALUES (:number, CAST(:date AS date), 'MANUAL', 'MANUAL', 'Schema test')
                        RETURNING id
                        """)
                .param("number", "JV/TEST/" + SEQUENCE.incrementAndGet() + "-" + System.nanoTime())
                .param("date", date)
                .query(Long.class)
                .single();
    }

    private void line(long entry, int position, String systemKey, String debit, String credit) {
        jdbc.sql("""
                        INSERT INTO journal_line (entry_id, position, account_id, debit_rupees, credit_rupees)
                        VALUES (:entry, :position, :account, CAST(:debit AS numeric), CAST(:credit AS numeric))
                        """)
                .param("entry", entry)
                .param("position", position)
                .param("account", account(systemKey))
                .param("debit", debit)
                .param("credit", credit)
                .update();
    }

    private long balancedEntry(String date) {
        return transactions().execute(status -> {
            long id = entry(date);
            line(id, 1, "CASH", "100.00", "0");
            line(id, 2, "SALES", "0", "100.00");
            return id;
        });
    }

    @Test
    void unbalancedEntryRejectedAtCommit() {
        assertThatThrownBy(() -> transactions().executeWithoutResult(status -> {
                    long id = entry("2026-11-01");
                    line(id, 1, "CASH", "100.00", "0");
                    line(id, 2, "SALES", "0", "90.00");
                }))
                .hasStackTraceContaining("does not balance");
        assertThatThrownBy(() -> transactions().executeWithoutResult(status -> {
                    long id = entry("2026-11-01");
                    line(id, 1, "CASH", "100.00", "0");
                }))
                .hasStackTraceContaining("does not balance");
    }

    @Test
    void balancedEntryCommits() {
        long id = balancedEntry("2026-11-01");
        assertThat(jdbc.sql("SELECT count(*) FROM journal_line WHERE entry_id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isEqualTo(2L);
    }

    @Test
    void postedLineCannotBeUpdatedOrDeleted() {
        long id = balancedEntry("2026-11-01");
        assertThatThrownBy(() -> jdbc.sql("UPDATE journal_line SET memo = 'x' WHERE entry_id = :id")
                        .param("id", id)
                        .update())
                .hasStackTraceContaining("Posted journal entries cannot be changed");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM journal_line WHERE entry_id = :id")
                        .param("id", id)
                        .update())
                .hasStackTraceContaining("Posted journal entries cannot be changed");
        assertThatThrownBy(() -> jdbc.sql("UPDATE journal_entry SET narration = 'x' WHERE id = :id")
                        .param("id", id)
                        .update())
                .hasStackTraceContaining("Posted journal entries cannot be changed");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM journal_entry WHERE id = :id")
                        .param("id", id)
                        .update())
                .hasStackTraceContaining("Posted journal entries cannot be changed");
    }

    @Test
    void lockedMonthRejectsEntry() {
        jdbc.sql("INSERT INTO period_lock (month, locked_by) VALUES (DATE '2001-03-01', 'Asha')")
                .update();
        try {
            assertThatThrownBy(() -> balancedEntry("2001-03-15")).hasStackTraceContaining("is locked");
        } finally {
            jdbc.sql("DELETE FROM period_lock WHERE month = DATE '2001-03-01'").update();
        }
    }

    @Test
    void auditLogRecordsUpdate() {
        long id = jdbc.sql("""
                        INSERT INTO tax_class (name, kind, hsn_code, rate_percent)
                        VALUES (:name, 'GOODS', '6204', 5) RETURNING id
                        """)
                .param("name", "Audit old " + System.nanoTime())
                .query(Long.class)
                .single();
        var newName = "Audit new " + System.nanoTime();
        transactions().executeWithoutResult(status -> {
            jdbc.sql("SELECT set_config('vaabhi.actor', 'Asha', true)")
                    .query(String.class)
                    .single();
            jdbc.sql("UPDATE tax_class SET name = :name WHERE id = :id")
                    .param("name", newName)
                    .param("id", id)
                    .update();
        });
        var rows = jdbc.sql("""
                        SELECT actor, old_values ->> 'name' AS old_name, new_values ->> 'name' AS new_name
                        FROM audit_log
                        WHERE table_name = 'tax_class' AND row_id = :id AND action = 'UPDATE'
                        """).param("id", Long.toString(id)).query().listOfRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor")).isEqualTo("Asha");
        assertThat(rows.get(0).get("new_name")).isEqualTo(newName);
        assertThat((String) rows.get(0).get("old_name")).startsWith("Audit old ");
    }

    @Test
    void chartSeeded() {
        for (var key : new String[] {"CASH", "BANK", "DEBTORS", "CUSTOMER_ADVANCES", "SALES", "EXPENSE_SALARY"}) {
            assertThat(account(key)).isPositive();
        }
        assertThat(jdbc.sql("SELECT count(*) FROM account WHERE type = 'EXPENSE'")
                        .query(Long.class)
                        .single())
                .isEqualTo(11L);
        assertThat(jdbc.sql("SELECT mode_accounts ->> 'UPI' FROM books_setting WHERE id = 1")
                        .query(String.class)
                        .single())
                .isEqualTo(Long.toString(account("BANK")));
    }

    @Test
    void systemAccountCannotBeDeactivated() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE account SET active = FALSE WHERE system_key = 'CASH'")
                        .update())
                .hasStackTraceContaining("System accounts");
    }

    @Test
    void journalSequenceSeeded() {
        var row = jdbc.sql("SELECT prefix, pad, reset FROM number_sequence WHERE kind = 'JOURNAL'")
                .query()
                .singleRow();
        assertThat(row.get("prefix")).isEqualTo("JV/{FY}/");
        assertThat(((Number) row.get("pad")).intValue()).isEqualTo(5);
        assertThat(row.get("reset")).isEqualTo("FY");
    }
}

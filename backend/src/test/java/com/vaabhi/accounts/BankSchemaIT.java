package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The bank reconciliation schema: the new accounts, journal source types, bank line rules and dedupe keys. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class BankSchemaIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    private static String today() {
        return LocalDate.now(IST).toString();
    }

    private static String dedupeKey() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2);
    }

    private long statement() {
        return jdbc.sql("""
                        INSERT INTO bank_statement (account_id, file_name, period_from, period_to)
                        VALUES (:account, 'statement.csv', CAST(:date AS date), CAST(:date AS date))
                        RETURNING id
                        """)
                .param("account", accountId("BANK"))
                .param("date", today())
                .query(Long.class)
                .single();
    }

    private void bankLine(long statement, long account, String debit, String credit, String key) {
        jdbc.sql("""
                        INSERT INTO bank_line (statement_id, account_id, line_date, description, debit_rupees,
                                               credit_rupees, dedupe_key)
                        VALUES (:statement, :account, CAST(:date AS date), 'Books test', CAST(:debit AS numeric),
                                CAST(:credit AS numeric), :key)
                        """)
                .param("statement", statement)
                .param("account", account)
                .param("date", today())
                .param("debit", debit)
                .param("credit", credit)
                .param("key", key)
                .update();
    }

    @Test
    void newAccountsSeeded() {
        var expected = Map.of(
                "5070", "EXPENSE_GATEWAY_FEES",
                "5080", "EXPENSE_COURIER",
                "5095", "EXPENSE_BANK_CHARGES");
        expected.forEach((code, key) -> {
            var row = jdbc.sql("SELECT code, type FROM account WHERE system_key = :key")
                    .param("key", key)
                    .query()
                    .singleRow();
            assertThat(row.get("code")).isEqualTo(code);
            assertThat(row.get("type")).isEqualTo("EXPENSE");
        });
        assertThat(jdbc.sql("SELECT name FROM account WHERE system_key = 'EXPENSE_COURIER'")
                        .query(String.class)
                        .single())
                .isEqualTo("Courier & COD charges");
    }

    @Test
    void journalAcceptsNewSourceTypes() {
        long entry = transactions().execute(status -> {
            long id = jdbc.sql("""
                            INSERT INTO journal_entry (number, entry_date, source_type, source_step, narration)
                            VALUES (:number, CAST(:date AS date), 'BANK_LINE', 'POST', 'Bank charges')
                            RETURNING id
                            """)
                    .param("number", unique("JV/BT/") + System.nanoTime())
                    .param("date", today())
                    .query(Long.class)
                    .single();
            jdbc.sql("""
                            INSERT INTO journal_line (entry_id, position, account_id, debit_rupees, credit_rupees)
                            VALUES (:entry, 1, :charges, 100.00, 0), (:entry, 2, :bank, 0, 100.00)
                            """)
                    .param("entry", id)
                    .param("charges", accountId("EXPENSE_BANK_CHARGES"))
                    .param("bank", accountId("BANK"))
                    .update();
            return id;
        });
        assertThat(entry).isPositive();
    }

    @Test
    void bankLineRejectsBothSides() {
        long statement = statement();
        long account = accountId("BANK");
        assertThatThrownBy(() -> bankLine(statement, account, "10.00", "10.00", dedupeKey()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dedupeKeyUniquePerAccount() {
        long account = accountId("BANK");
        var key = dedupeKey();
        bankLine(statement(), account, "10.00", "0", key);
        assertThatThrownBy(() -> bankLine(statement(), account, "10.00", "0", key))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

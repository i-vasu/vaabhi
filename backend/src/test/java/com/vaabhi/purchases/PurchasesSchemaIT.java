package com.vaabhi.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.accounts.BooksFixtures;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The purchases schema: supplier bills, payments, TDS, fixed assets and the guards that freeze posted rows. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class PurchasesSchemaIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    private static String today() {
        return LocalDate.now(IST).toString();
    }

    private static String firstOfMonth() {
        return LocalDate.now(IST).withDayOfMonth(1).toString();
    }

    private long supplier() {
        return jdbc.sql("INSERT INTO party (name, is_supplier) VALUES (:name, TRUE) RETURNING id")
                .param("name", unique("Supplier"))
                .query(Long.class)
                .single();
    }

    private long bill(long party, String billNo, String taxable, String tax, String total, String tds, String payable) {
        return jdbc.sql("""
                        INSERT INTO supplier_bill (party_id, bill_no, bill_date, taxable_rupees, tax_rupees,
                                                   total_rupees, tds_rupees, payable_rupees)
                        VALUES (:party, :number, CAST(:date AS date), CAST(:taxable AS numeric),
                                CAST(:tax AS numeric), CAST(:total AS numeric), CAST(:tds AS numeric),
                                CAST(:payable AS numeric))
                        RETURNING id
                        """)
                .param("party", party)
                .param("number", billNo)
                .param("date", today())
                .param("taxable", taxable)
                .param("tax", tax)
                .param("total", total)
                .param("tds", tds)
                .param("payable", payable)
                .query(Long.class)
                .single();
    }

    private long validBill() {
        return bill(supplier(), unique("BILL-"), "1000", "0", "1000", "0", "1000");
    }

    @Test
    void accountsAndSeedsPresent() {
        List.of("5100", "5200", "4900", "5210", "1500", "1501", "1510", "1511", "1520", "1521", "1530", "1531")
                .forEach(code -> assertThat(jdbc.sql("SELECT count(*) FROM account WHERE code = :code")
                                .param("code", code)
                                .query(Long.class)
                                .single())
                        .as("account %s", code)
                        .isEqualTo(1));

        assertThat(jdbc.sql("SELECT count(*) FROM asset_class")
                        .query(Long.class)
                        .single())
                .isGreaterThanOrEqualTo(4);
        assertThat(jdbc.sql("SELECT life_years FROM asset_class WHERE name = 'Plant & machinery'")
                        .query(Integer.class)
                        .single())
                .isEqualTo(15);

        assertThat(jdbc.sql("SELECT code FROM tds_section").query(String.class).list())
                .contains("194C-I", "194C-O", "194I-B", "194I-A", "194J", "194H");
    }

    @Test
    void billPayableMustEqualTotalLessTds() {
        long party = supplier();
        assertThatThrownBy(() -> bill(party, unique("BILL-"), "1000", "0", "1000", "10", "1000"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stockLineNeedsItemAndQuantity() {
        long bill = validBill();
        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO supplier_bill_line (bill_id, position, kind, description, account_id,
                                                                quantity, taxable_rupees, gst_rate_percent,
                                                                total_rupees)
                                VALUES (:bill, 1, 'STOCK', 'Cloth', :account, 1, 500, 0, 500)
                                """)
                        .param("bill", bill)
                        .param("account", accountId("PURCHASES"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void postedBillRejectsAmountChange() {
        long bill = validBill();
        transactions().executeWithoutResult(status -> {
            long entry = jdbc.sql("""
                            INSERT INTO journal_entry (number, entry_date, source_type, source_id, source_step,
                                                       narration)
                            VALUES (:number, CAST(:date AS date), 'SUPPLIER_BILL', :bill, 'ISSUE',
                                    'Books test bill')
                            RETURNING id
                            """)
                    .param("number", unique("JV/BT/") + System.nanoTime())
                    .param("date", today())
                    .param("bill", bill)
                    .query(Long.class)
                    .single();
            jdbc.sql("""
                            INSERT INTO journal_line (entry_id, position, account_id, debit_rupees, credit_rupees)
                            VALUES (:entry, 1, :purchases, 1000.00, 0), (:entry, 2, :creditors, 0, 1000.00)
                            """)
                    .param("entry", entry)
                    .param("purchases", accountId("PURCHASES"))
                    .param("creditors", accountId("CREDITORS"))
                    .update();
        });

        assertThatThrownBy(() -> jdbc.sql("""
                                UPDATE supplier_bill
                                SET total_rupees = 900, taxable_rupees = 900, payable_rupees = 900
                                WHERE id = :id
                                """).param("id", bill).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("in the books");

        jdbc.sql("UPDATE supplier_bill SET notes = 'ok' WHERE id = :id")
                .param("id", bill)
                .update();
        assertThat(jdbc.sql("SELECT notes FROM supplier_bill WHERE id = :id")
                        .param("id", bill)
                        .query(String.class)
                        .single())
                .isEqualTo("ok");
    }

    @Test
    void depreciationMonthUniquePerAsset() {
        long asset = jdbc.sql("""
                        INSERT INTO fixed_asset (name, asset_class_id, cost_rupees, put_to_use_on)
                        VALUES ('Laptop', (SELECT id FROM asset_class WHERE name = 'Computers'),
                                50000, CAST(:date AS date))
                        RETURNING id
                        """).param("date", today()).query(Long.class).single();

        jdbc.sql("""
                        INSERT INTO asset_depreciation (asset_id, month, amount_rupees)
                        VALUES (:asset, CAST(:month AS date), 1000)
                        """).param("asset", asset).param("month", firstOfMonth()).update();

        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO asset_depreciation (asset_id, month, amount_rupees)
                                VALUES (:asset, CAST(:month AS date), 1000)
                                """)
                        .param("asset", asset)
                        .param("month", firstOfMonth())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

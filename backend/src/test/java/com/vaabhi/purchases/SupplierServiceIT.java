package com.vaabhi.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.accounts.BooksFixtures;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Suppliers: creating them, their PAN and TDS section, and the balances and statement built from bills and payments. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class SupplierServiceIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private SupplierService suppliers;

    private static SupplierForm form(String name) {
        return new SupplierForm(name, null, null, null, null, null, null, null, null, null);
    }

    private long supplier() {
        return jdbc.sql("INSERT INTO party (name, is_supplier) VALUES (:name, TRUE) RETURNING id")
                .param("name", unique("Supplier"))
                .query(Long.class)
                .single();
    }

    private long tdsSection(String code) {
        return jdbc.sql("SELECT id FROM tds_section WHERE code = :code")
                .param("code", code)
                .query(Long.class)
                .single();
    }

    private long bill(long party, LocalDate billDate, @Nullable LocalDate dueDate, String payable) {
        return jdbc.sql("""
                        INSERT INTO supplier_bill (party_id, bill_no, bill_date, due_date, taxable_rupees,
                                                   tax_rupees, total_rupees, tds_rupees, payable_rupees)
                        VALUES (:party, :number, CAST(:billDate AS date), CAST(:dueDate AS date),
                                CAST(:payable AS numeric), 0, CAST(:payable AS numeric), 0,
                                CAST(:payable AS numeric))
                        RETURNING id
                        """)
                .param("party", party)
                .param("number", unique("BILL-"))
                .param("billDate", billDate)
                .param("dueDate", dueDate)
                .param("payable", payable)
                .query(Long.class)
                .single();
    }

    private void cancel(long bill) {
        jdbc.sql("""
                        UPDATE supplier_bill
                        SET status = 'CANCELLED', cancelled_on = CAST(:date AS date), cancel_reason = 'Test'
                        WHERE id = :id
                        """)
                .param("date", LocalDate.now(IST))
                .param("id", bill)
                .update();
    }

    private long payment(long party, LocalDate paidOn, String amount) {
        return jdbc.sql("""
                        INSERT INTO supplier_payment (party_id, paid_on, mode, amount_rupees)
                        VALUES (:party, CAST(:paidOn AS date), 'CASH', CAST(:amount AS numeric))
                        RETURNING id
                        """)
                .param("party", party)
                .param("paidOn", paidOn)
                .param("amount", amount)
                .query(Long.class)
                .single();
    }

    private void allocate(long payment, long bill, String amount) {
        jdbc.sql("""
                        INSERT INTO supplier_payment_allocation (payment_id, bill_id, amount_rupees)
                        VALUES (:payment, :bill, CAST(:amount AS numeric))
                        """)
                .param("payment", payment)
                .param("bill", bill)
                .param("amount", amount)
                .update();
    }

    @Test
    void createThenListShowsZeroBalance() {
        var name = unique("Supplier");
        long id = suppliers.create(form(name));

        var balance = suppliers.list(name).stream()
                .filter(b -> b.partyId() == id)
                .findFirst()
                .orElseThrow();

        assertThat(balance.name()).isEqualTo(name);
        assertThat(balance.billedRupees()).isEqualByComparingTo("0");
        assertThat(balance.paidRupees()).isEqualByComparingTo("0");
        assertThat(balance.tdsRupees()).isEqualByComparingTo("0");
        assertThat(balance.balanceRupees()).isEqualByComparingTo("0");
        assertThat(balance.oldestUnpaidDue()).isNull();
        assertThat(balance.overdue()).isFalse();
    }

    @Test
    void panAndTdsSectionStored() {
        long section = tdsSection("194J");
        long id = suppliers.create(
                new SupplierForm(unique("Supplier"), null, null, null, "ABCDE1234F", null, null, null, null, section));

        var statement = suppliers.statement(id);

        assertThat(statement.pan()).isEqualTo("ABCDE1234F");
        assertThat(statement.tdsSectionId()).isEqualTo(section);
    }

    @Test
    void balanceIsPayableMinusPayments() {
        var today = LocalDate.now(IST);
        long supplier = supplier();
        long first = bill(supplier, today.minusDays(20), today.minusDays(10), "1000");
        bill(supplier, today.minusDays(5), today.plusDays(5), "500");
        long paid = payment(supplier, today.minusDays(1), "600");
        allocate(paid, first, "600");

        var balance = suppliers.statement(supplier).balance();

        assertThat(balance.billedRupees()).isEqualByComparingTo("1500");
        assertThat(balance.paidRupees()).isEqualByComparingTo("600");
        assertThat(balance.balanceRupees()).isEqualByComparingTo("900");
        assertThat(balance.oldestUnpaidDue()).isEqualTo(today.minusDays(10));
        assertThat(balance.overdue()).isTrue();
    }

    @Test
    void fullyAllocatedBillNotOldest() {
        var today = LocalDate.now(IST);
        long supplier = supplier();
        long first = bill(supplier, today.minusDays(20), today.minusDays(10), "1000");
        bill(supplier, today.minusDays(5), today.plusDays(5), "500");
        long paid = payment(supplier, today.minusDays(1), "1000");
        allocate(paid, first, "1000");

        var balance = suppliers.statement(supplier).balance();

        assertThat(balance.balanceRupees()).isEqualByComparingTo("500");
        assertThat(balance.oldestUnpaidDue()).isEqualTo(today.plusDays(5));
        assertThat(balance.overdue()).isFalse();
    }

    @Test
    void cancelledBillsIgnored() {
        var today = LocalDate.now(IST);
        long supplier = supplier();
        bill(supplier, today.minusDays(20), today.plusDays(5), "1000");
        cancel(bill(supplier, today.minusDays(25), today.minusDays(15), "7000"));

        var balance = suppliers.statement(supplier).balance();

        assertThat(balance.billedRupees()).isEqualByComparingTo("1000");
        assertThat(balance.balanceRupees()).isEqualByComparingTo("1000");
        assertThat(balance.oldestUnpaidDue()).isEqualTo(today.plusDays(5));
    }

    @Test
    void statementRunningBalance() {
        var today = LocalDate.now(IST);
        long supplier = supplier();
        bill(supplier, today.minusDays(10), today.minusDays(10), "1000");
        payment(supplier, today.minusDays(9), "400");

        var entries = suppliers.statement(supplier).entries();

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).kind()).isEqualTo("BILL");
        assertThat(entries.get(0).billedRupees()).isEqualByComparingTo("1000");
        assertThat(entries.get(0).runningBalanceRupees()).isEqualByComparingTo("1000");
        assertThat(entries.get(1).kind()).isEqualTo("PAYMENT");
        assertThat(entries.get(1).paidRupees()).isEqualByComparingTo("400");
        assertThat(entries.get(1).runningBalanceRupees()).isEqualByComparingTo("600");
    }

    @Test
    void unknownTdsSectionIs400() {
        assertThatThrownBy(() -> suppliers.create(
                        new SupplierForm(unique("Supplier"), null, null, null, null, null, null, null, null, 999_999_999L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Pick a TDS section");
    }
}

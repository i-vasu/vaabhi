package com.vaabhi.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.accounts.BooksFixtures;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Supplier payments: allocation over bills, advances, payment from a bank line and the posted guard. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class SupplierPaymentServiceIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal NO_GST = new BigDecimal("0");

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private SupplierBillService bills;

    @Autowired
    private SupplierPaymentService payments;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    private static LocalDate today() {
        return LocalDate.now(IST);
    }

    private long supplier() {
        return suppliers.create(
                new SupplierForm(unique("Supplier"), null, null, null, null, null, null, null, null, null));
    }

    private SupplierBillView bill(
            long party, String taxable, @Nullable LocalDate billDate, @Nullable LocalDate dueDate) {
        var line = new SupplierBillLineForm(
                SupplierBillLineForm.Kind.EXPENSE,
                "Freight",
                accountId("EXPENSE_OTHER"),
                null,
                null,
                null,
                null,
                new BigDecimal(taxable),
                NO_GST);
        return bills.create(
                new SupplierBillForm(
                        party, unique("BILL-"), billDate, dueDate, null, null, Rupees.ZERO, null, List.of(line), null),
                null);
    }

    private SupplierPaymentView pay(
            long party, String amount, @Nullable List<SupplierPaymentForm.PaymentAllocationInput> allocations) {
        return payments.create(
                new SupplierPaymentForm(party, today(), "BANK", new BigDecimal(amount), null, null, allocations), null);
    }

    /** An unmatched bank line on the Bank account. */
    private long bankLine(LocalDate date, String debit, String credit, @Nullable String reference) {
        long account = accountId("BANK");
        long statement = jdbc.sql("""
                        INSERT INTO bank_statement (account_id, file_name, period_from, period_to, line_count)
                        VALUES (:account, 'test.csv', CAST(:date AS date), CAST(:date AS date), 1)
                        RETURNING id
                        """)
                .param("account", account)
                .param("date", date)
                .query(Long.class)
                .single();
        return jdbc.sql("""
                        INSERT INTO bank_line (statement_id, account_id, line_date, description, reference,
                                               debit_rupees, credit_rupees, dedupe_key)
                        VALUES (:statement, :account, CAST(:date AS date), 'Supplier payment', :reference,
                                :debit, :credit, :key)
                        RETURNING id
                        """)
                .param("statement", statement)
                .param("account", account)
                .param("date", date)
                .param("reference", reference)
                .param("debit", new BigDecimal(debit))
                .param("credit", new BigDecimal(credit))
                .param("key", unique("dedupe-") + System.nanoTime())
                .query(Long.class)
                .single();
    }

    @Test
    void autoAllocatesOldestFirst() {
        long party = supplier();
        var first = bill(party, "1000", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1));
        var second = bill(party, "500", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 15));

        var payment = pay(party, "1200", null);

        assertThat(payment.amountRupees()).isEqualByComparingTo("1200.00");
        assertThat(payment.allocatedRupees()).isEqualByComparingTo("1200.00");
        assertThat(payment.posted()).isFalse();
        assertThat(payment.allocations()).hasSize(2);
        assertThat(payment.allocations().get(0).billId()).isEqualTo(first.id());
        assertThat(payment.allocations().get(0).amountRupees()).isEqualByComparingTo("1000.00");
        assertThat(payment.allocations().get(1).billId()).isEqualTo(second.id());
        assertThat(payment.allocations().get(1).amountRupees()).isEqualByComparingTo("200.00");
    }

    @Test
    void secondPaymentFillsRest() {
        long party = supplier();
        bill(party, "1000", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1));
        var second = bill(party, "500", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 15));
        pay(party, "1200", null);

        var payment = pay(party, "300", null);

        assertThat(payment.amountRupees()).isEqualByComparingTo("300.00");
        assertThat(payment.allocatedRupees()).isEqualByComparingTo("300.00");
        assertThat(payment.allocations()).hasSize(1);
        assertThat(payment.allocations().getFirst().billId()).isEqualTo(second.id());
        assertThat(payment.allocations().getFirst().amountRupees()).isEqualByComparingTo("300.00");
    }

    @Test
    void overAllocationRejectedAndNothingSaved() {
        long party = supplier();
        var bill = bill(party, "1000", today(), today().plusDays(10));

        assertThatThrownBy(() -> pay(
                        party,
                        "1500",
                        List.of(new SupplierPaymentForm.PaymentAllocationInput(bill.id(), new BigDecimal("1200")))))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(400);
                    assertThat(e.getReason()).contains("More than this bill's balance");
                });

        assertThat(jdbc.sql("SELECT count(*) FROM supplier_payment WHERE party_id = :party")
                        .param("party", party)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM supplier_payment_allocation WHERE bill_id = :bill")
                        .param("bill", bill.id())
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void unallocatedRemainderIsAdvance() {
        long party = supplier();
        bill(party, "1000", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1));
        bill(party, "500", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 15));

        var payment = pay(party, "2000", null);

        assertThat(payment.allocatedRupees()).isEqualByComparingTo("1500.00");
        var balance = suppliers.statement(party).balance();
        assertThat(balance.paidRupees()).isEqualByComparingTo("2000.00");
        assertThat(balance.balanceRupees()).isEqualByComparingTo("-500.00");
    }

    @Test
    void paidNowCreatesPaymentForPayable() {
        long section = jdbc.sql("SELECT id FROM tds_section WHERE code = '194C-O'")
                .query(Long.class)
                .single();
        long party = suppliers.create(
                new SupplierForm(unique("Supplier"), null, null, null, "ABCDE1234F", null, null, null, null, section));
        var line = new SupplierBillLineForm(
                SupplierBillLineForm.Kind.EXPENSE,
                "Freight",
                accountId("EXPENSE_OTHER"),
                null,
                null,
                null,
                null,
                new BigDecimal("10000"),
                NO_GST);

        var bill = bills.create(
                new SupplierBillForm(
                        party,
                        unique("BILL-"),
                        today(),
                        null,
                        null,
                        null,
                        new BigDecimal("200"),
                        null,
                        List.of(line),
                        new SupplierBillForm.PaidNow("CASH", "CASH-1")),
                null);

        assertThat(bill.payableRupees()).isEqualByComparingTo("9800.00");
        assertThat(bill.paidRupees()).isEqualByComparingTo("9800.00");
        var recorded = payments.list(party);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().amountRupees()).isEqualByComparingTo("9800.00");
        assertThat(recorded.getFirst().mode()).isEqualTo("CASH");
        assertThat(recorded.getFirst().allocations()).hasSize(1);
        assertThat(recorded.getFirst().allocations().getFirst().billId()).isEqualTo(bill.id());
    }

    @Test
    void fromBankLineTakesDebitLine() {
        long party = supplier();
        var bill = bill(party, "1000", today(), null);
        var date = today();
        long line = bankLine(date, "750", "0", "UTR-750");

        var payment = payments.fromBankLine(new BankLinePaymentForm(line, party, null), null);

        assertThat(payment.amountRupees()).isEqualByComparingTo("750.00");
        assertThat(payment.allocatedRupees()).isEqualByComparingTo("750.00");
        assertThat(payment.mode()).isEqualTo("BANK");
        assertThat(payment.bankLineId()).isEqualTo(line);
        assertThat(payment.paidOn()).isEqualTo(date);
        assertThat(payment.reference()).isEqualTo("UTR-750");
        assertThat(payment.allocations()).hasSize(1);
        assertThat(payment.allocations().getFirst().billId()).isEqualTo(bill.id());
    }

    @Test
    void fromCreditLineIs409() {
        long party = supplier();
        long line = bankLine(today(), "0", "750", "CREDIT-1");

        assertThatThrownBy(() -> payments.fromBankLine(new BankLinePaymentForm(line, party, null), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(409);
                    assertThat(e.getReason()).contains("Only an unmatched money-out line");
                });
    }

    @Test
    void deletePostedIs409() {
        long party = supplier();
        bill(party, "1000", today(), null);
        var payment = pay(party, "500", null);
        transactions().executeWithoutResult(status -> {
            long entry = jdbc.sql("""
                            INSERT INTO journal_entry (number, entry_date, source_type, source_id, source_step,
                                                       narration)
                            VALUES (:number, CAST(:date AS date), 'SUPPLIER_PAYMENT', :payment, 'PAYMENT',
                                    'Books test payment')
                            RETURNING id
                            """)
                    .param("number", unique("JV/BT/") + System.nanoTime())
                    .param("date", today())
                    .param("payment", payment.id())
                    .query(Long.class)
                    .single();
            jdbc.sql("""
                            INSERT INTO journal_line (entry_id, position, account_id, debit_rupees, credit_rupees)
                            VALUES (:entry, 1, :creditors, 500.00, 0), (:entry, 2, :bank, 0, 500.00)
                            """)
                    .param("entry", entry)
                    .param("creditors", accountId("CREDITORS"))
                    .param("bank", accountId("BANK"))
                    .update();
        });

        assertThatThrownBy(() -> payments.delete(payment.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(409);
                    assertThat(e.getReason()).contains("reverse it instead");
                });

        assertThat(jdbc.sql("SELECT count(*) FROM supplier_payment WHERE id = :id")
                        .param("id", payment.id())
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }
}

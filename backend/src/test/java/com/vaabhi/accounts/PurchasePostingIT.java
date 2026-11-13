package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.purchases.BankLinePaymentForm;
import com.vaabhi.purchases.SupplierBillForm;
import com.vaabhi.purchases.SupplierBillLineForm;
import com.vaabhi.purchases.SupplierBillService;
import com.vaabhi.purchases.SupplierBillView;
import com.vaabhi.purchases.SupplierForm;
import com.vaabhi.purchases.SupplierPaymentForm;
import com.vaabhi.purchases.SupplierPaymentService;
import com.vaabhi.purchases.SupplierService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** What the books hold for supplier bills, their cancellation and supplier payments. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class PurchasePostingIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private SupplierBillService bills;

    @Autowired
    private SupplierPaymentService payments;

    private static LocalDate today() {
        return LocalDate.now(IST);
    }

    private long supplier() {
        return suppliers.create(new SupplierForm(
                unique("Supplier"), null, null, null, null, null, null, null, null, null));
    }

    private String supplierName(long party) {
        return jdbc.sql("SELECT name FROM party WHERE id = :id")
                .param("id", party)
                .query(String.class)
                .single();
    }

    private long stockItem() {
        return jdbc.sql("""
                        INSERT INTO stock_item (name, unit, reorder_level, cost_rupees)
                        VALUES (:name, 'M', 0, 0) RETURNING id
                        """)
                .param("name", unique("Cloth"))
                .query(Long.class)
                .single();
    }

    private long computersClass() {
        return jdbc.sql("SELECT id FROM asset_class WHERE name = 'Computers'")
                .query(Long.class)
                .single();
    }

    /** An unmatched money-out bank line on the Bank account. */
    private long bankLine(LocalDate date, String debit, String reference) {
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
                                :debit, 0, :key)
                        RETURNING id
                        """)
                .param("statement", statement)
                .param("account", account)
                .param("date", date)
                .param("reference", reference)
                .param("debit", new BigDecimal(debit))
                .param("key", unique("dedupe-") + System.nanoTime())
                .query(Long.class)
                .single();
    }

    private record BankLineRow(
            String status,
            @Nullable Long journalEntryId,
            @Nullable String matchedBy,
            @Nullable OffsetDateTime matchedAt) {}

    private BigDecimal partyNet(long account, long party) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(debit_rupees - credit_rupees), 0)
                        FROM journal_line WHERE account_id = :account AND party_id = :party
                        """)
                .param("account", account)
                .param("party", party)
                .query(BigDecimal.class)
                .single();
    }

    private static SupplierBillLineForm stockLine(long item, String quantity, String taxable, String gst) {
        return new SupplierBillLineForm(
                SupplierBillLineForm.Kind.STOCK,
                "Cloth",
                null,
                item,
                new BigDecimal(quantity),
                null,
                null,
                new BigDecimal(taxable),
                new BigDecimal(gst));
    }

    private static SupplierBillLineForm expenseLine(long account, String taxable, String gst) {
        return new SupplierBillLineForm(
                SupplierBillLineForm.Kind.EXPENSE,
                "Freight",
                account,
                null,
                null,
                null,
                null,
                new BigDecimal(taxable),
                new BigDecimal(gst));
    }

    private static SupplierBillLineForm assetLine(long assetClass, String taxable, String gst) {
        return new SupplierBillLineForm(
                SupplierBillLineForm.Kind.ASSET,
                "Laptop",
                null,
                null,
                null,
                assetClass,
                null,
                new BigDecimal(taxable),
                new BigDecimal(gst));
    }

    private SupplierBillView createBill(
            long party, @Nullable Long tdsSection, String tds, List<SupplierBillLineForm> lines) {
        return bills.create(
                new SupplierBillForm(
                        party, unique("BILL-"), today(), null, null, tdsSection, new BigDecimal(tds), null, lines,
                        null),
                null);
    }

    private void expenseBill(long party, String taxable) {
        bills.create(
                new SupplierBillForm(
                        party,
                        unique("BILL-"),
                        today(),
                        null,
                        null,
                        null,
                        Rupees.ZERO,
                        null,
                        List.of(expenseLine(accountId("EXPENSE_OTHER"), taxable, "0")),
                        null),
                null);
    }

    @Test
    void billIssuePostsPurchasesExpenseAssetGstCreditorsTds() {
        long party = supplier();
        long item = stockItem();
        var first = createBill(
                party,
                null,
                "0",
                List.of(
                        stockLine(item, "10", "1000", "5"),
                        expenseLine(accountId("EXPENSE_OTHER"), "500", "18"),
                        assetLine(computersClass(), "40000", "18")));

        sweeper.sweepNow();

        var issue = entry("SUPPLIER_BILL", first.id(), "ISSUE");
        assertThat(issue.narration()).startsWith("Bill " + first.billNo());
        assertThat(netInEntry(issue.id(), accountId("PURCHASES"))).isEqualByComparingTo("1000");
        assertThat(netInEntry(issue.id(), accountId("EXPENSE_OTHER"))).isEqualByComparingTo("500");
        assertThat(netInEntry(issue.id(), accountId("ASSET_COMPUTERS"))).isEqualByComparingTo("40000");
        assertThat(netInEntry(issue.id(), accountId("INPUT_GST"))).isEqualByComparingTo("7340");
        assertThat(netInEntry(issue.id(), accountId("CREDITORS"))).isEqualByComparingTo("-48840");
        assertThat(netInEntry(issue.id(), accountId("TDS_PAYABLE"))).isEqualByComparingTo("0");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM journal_line WHERE entry_id = :entry AND party_id = :party")
                        .param("entry", issue.id())
                        .param("party", party)
                        .query(Long.class)
                        .single())
                .isEqualTo(5);

        long section = jdbc.sql("SELECT id FROM tds_section WHERE code = '194C-O'")
                .query(Long.class)
                .single();
        var second = createBill(party, section, "100", List.of(expenseLine(accountId("EXPENSE_OTHER"), "1000", "18")));

        sweeper.sweepNow();

        var secondIssue = entry("SUPPLIER_BILL", second.id(), "ISSUE");
        assertThat(netInEntry(secondIssue.id(), accountId("TDS_PAYABLE"))).isEqualByComparingTo("-100");
        assertThat(netInEntry(secondIssue.id(), accountId("CREDITORS"))).isEqualByComparingTo("-1080");
        assertThat(partyNet(accountId("CREDITORS"), party)).isEqualByComparingTo("-49920");
    }

    @Test
    void cancelAfterPostingMirrors() {
        long party = supplier();
        var bill = createBill(
                party, null, "0", List.of(expenseLine(accountId("EXPENSE_OTHER"), "1000", "18")));
        sweeper.sweepNow();
        entry("SUPPLIER_BILL", bill.id(), "ISSUE");

        bills.cancel(bill.id(), today(), "Typed twice", null);
        sweeper.sweepNow();

        var cancel = entry("SUPPLIER_BILL", bill.id(), "CANCEL");
        assertThat(cancel.narration()).isEqualTo("Bill " + bill.billNo() + " cancelled");
        assertThat(netInEntry(cancel.id(), accountId("EXPENSE_OTHER"))).isEqualByComparingTo("-1000");
        assertThat(netInEntry(cancel.id(), accountId("INPUT_GST"))).isEqualByComparingTo("-180");
        assertThat(netInEntry(cancel.id(), accountId("CREDITORS"))).isEqualByComparingTo("1180");
        assertThat(partyNet(accountId("CREDITORS"), party)).isEqualByComparingTo("0");
    }

    @Test
    void cancelBeforePostingPostsNothing() {
        long party = supplier();
        var bill = createBill(
                party, null, "0", List.of(expenseLine(accountId("EXPENSE_OTHER"), "1000", "0")));

        bills.cancel(bill.id(), today(), "Typed twice", null);
        sweeper.sweepNow();

        assertThat(entries("SUPPLIER_BILL", bill.id())).isEmpty();
    }

    @Test
    void paymentPostsCreditorsAgainstBank() {
        long party = supplier();
        expenseBill(party, "1000");
        var payment = payments.create(
                new SupplierPaymentForm(
                        party, today(), "BANK", new BigDecimal("600"), "UTR-600", null, null),
                null);

        sweeper.sweepNow();

        var posted = entry("SUPPLIER_PAYMENT", payment.id(), "PAYMENT");
        assertThat(posted.narration()).isEqualTo("Payment to " + supplierName(party) + " (UTR-600)");
        assertThat(netInEntry(posted.id(), accountId("CREDITORS"))).isEqualByComparingTo("600");
        assertThat(netInEntry(posted.id(), settings.accountFor("BANK"))).isEqualByComparingTo("-600");
        assertThat(partyNet(accountId("CREDITORS"), party)).isEqualByComparingTo("-400");
    }

    @Test
    void paymentFromBankLineMarksLineMatched() {
        long party = supplier();
        expenseBill(party, "1000");
        long line = bankLine(today(), "750", "UTR-750");
        var payment = payments.fromBankLine(new BankLinePaymentForm(line, party, null), null);

        sweeper.sweepNow();

        var posted = entry("SUPPLIER_PAYMENT", payment.id(), "PAYMENT");
        var matched = jdbc.sql("""
                        SELECT status, journal_entry_id, matched_by, matched_at
                        FROM bank_line WHERE id = :id
                        """)
                .param("id", line)
                .query(BankLineRow.class)
                .single();
        assertThat(matched.status()).isEqualTo("MATCHED");
        assertThat(matched.journalEntryId()).isEqualTo(posted.id());
        assertThat(matched.matchedBy()).isEqualTo("system");
        assertThat(matched.matchedAt()).isNotNull();
    }
}

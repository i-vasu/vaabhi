package com.vaabhi.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.accounts.BooksFixtures;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Supplier bills: stock and assets bought on them, edits before posting, cancellation and TDS suggestions. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class SupplierBillServiceIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal NO_GST = new BigDecimal("0");

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private SupplierBillService bills;

    private static LocalDate today() {
        return LocalDate.now(IST);
    }

    private long supplier(@Nullable String pan, @Nullable Long tdsSectionId) {
        return suppliers.create(new SupplierForm(
                unique("Supplier"), null, null, null, pan, null, null, null, null, tdsSectionId));
    }

    private long tdsSection(String code) {
        return jdbc.sql("SELECT id FROM tds_section WHERE code = :code")
                .param("code", code)
                .query(Long.class)
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

    private long expenseAccount() {
        return jdbc.sql("SELECT id FROM account WHERE type = 'EXPENSE' AND active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
    }

    private long assetClass() {
        return jdbc.sql("SELECT id FROM asset_class WHERE active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
    }

    private BigDecimal stockOnHand(long item) {
        return jdbc.sql("SELECT COALESCE(SUM(quantity), 0) FROM stock_movement WHERE item_id = :id")
                .param("id", item)
                .query(BigDecimal.class)
                .single();
    }

    private static SupplierBillLineForm stockLine(long item, String quantity, String taxable) {
        return new SupplierBillLineForm(
                SupplierBillLineForm.Kind.STOCK,
                "Cloth",
                null,
                item,
                new BigDecimal(quantity),
                null,
                null,
                new BigDecimal(taxable),
                NO_GST);
    }

    private static SupplierBillLineForm expenseLine(long account, String taxable) {
        return new SupplierBillLineForm(
                SupplierBillLineForm.Kind.EXPENSE,
                "Freight",
                account,
                null,
                null,
                null,
                null,
                new BigDecimal(taxable),
                NO_GST);
    }

    private static SupplierBillLineForm assetLine(long assetClass, String taxable) {
        return new SupplierBillLineForm(
                SupplierBillLineForm.Kind.ASSET,
                "Laptop",
                null,
                null,
                null,
                assetClass,
                null,
                new BigDecimal(taxable),
                NO_GST);
    }

    private static SupplierBillForm form(long party, String billNo, List<SupplierBillLineForm> lines) {
        return new SupplierBillForm(
                party, billNo, today(), null, null, null, BigDecimal.ZERO, null, lines, null);
    }

    @Test
    void createWithStockExpenseAssetLines() {
        long party = supplier(null, null);
        long item = stockItem();
        long assetClass = assetClass();

        var bill = bills.create(
                form(party, unique("BILL-"), List.of(
                        stockLine(item, "10", "1000"),
                        expenseLine(expenseAccount(), "400"),
                        assetLine(assetClass, "2000"))),
                null);

        assertThat(bill.lines()).hasSize(3);
        assertThat(bill.taxableRupees()).isEqualByComparingTo("3400");
        assertThat(bill.cgstRupees()).isEqualByComparingTo("0");
        assertThat(bill.sgstRupees()).isEqualByComparingTo("0");
        assertThat(bill.igstRupees()).isEqualByComparingTo("0");
        assertThat(bill.totalRupees()).isEqualByComparingTo("3400");
        assertThat(bill.payableRupees()).isEqualByComparingTo("3400");
        assertThat(bill.posted()).isFalse();

        assertThat(stockOnHand(item)).isEqualByComparingTo("10");
        assertThat(jdbc.sql("SELECT cost_rupees FROM stock_item WHERE id = :id")
                        .param("id", item)
                        .query(BigDecimal.class)
                        .single())
                .isEqualByComparingTo("100.00");

        long assetLine = bill.lines().stream()
                .filter(line -> "ASSET".equals(line.kind()))
                .findFirst()
                .orElseThrow()
                .id();
        assertThat(jdbc.sql("SELECT cost_rupees FROM fixed_asset WHERE bill_line_id = :line")
                        .param("line", assetLine)
                        .query(BigDecimal.class)
                        .single())
                .isEqualByComparingTo("2000.00");
    }

    @Test
    void duplicateBillNoIs409() {
        long party = supplier(null, null);
        long account = expenseAccount();
        bills.create(form(party, "INV-77", List.of(expenseLine(account, "400"))), null);

        assertThatThrownBy(() -> bills.create(form(party, "inv-77", List.of(expenseLine(account, "400"))), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already recorded");
    }

    @Test
    void updateUnpostedBillMovesStockByDifference() {
        long party = supplier(null, null);
        long item = stockItem();
        var bill = bills.create(form(party, unique("BILL-"), List.of(stockLine(item, "10", "1000"))), null);

        bills.update(bill.id(), form(party, bill.billNo(), List.of(stockLine(item, "6", "600"))), null);

        assertThat(stockOnHand(item)).isEqualByComparingTo("6");
    }

    @Test
    void cancelReturnsStock() {
        long party = supplier(null, null);
        long item = stockItem();
        var bill = bills.create(form(party, unique("BILL-"), List.of(stockLine(item, "10", "1000"))), null);

        var cancelled = bills.cancel(bill.id(), today(), "Wrong bill", null);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(stockOnHand(item)).isEqualByComparingTo("0");
    }

    @Test
    void cancelWithAllocationIs409() {
        long party = supplier(null, null);
        var bill = bills.create(
                form(party, unique("BILL-"), List.of(expenseLine(expenseAccount(), "1000"))), null);
        long payment = jdbc.sql("""
                        INSERT INTO supplier_payment (party_id, paid_on, mode, amount_rupees)
                        VALUES (:party, CAST(:date AS date), 'CASH', 1000) RETURNING id
                        """)
                .param("party", party)
                .param("date", today())
                .query(Long.class)
                .single();
        jdbc.sql("""
                        INSERT INTO supplier_payment_allocation (payment_id, bill_id, amount_rupees)
                        VALUES (:payment, :bill, 1000)
                        """)
                .param("payment", payment)
                .param("bill", bill.id())
                .update();

        assertThatThrownBy(() -> bills.cancel(bill.id(), today(), "Wrong bill", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Remove the payments");
    }

    @Test
    void updatePostedBillIs409() {
        long party = supplier(null, null);
        var bill = bills.create(
                form(party, unique("BILL-"), List.of(expenseLine(expenseAccount(), "400"))), null);
        jdbc.sql("""
                        INSERT INTO journal_entry (number, entry_date, source_type, source_id, source_step, narration)
                        VALUES (:number, CAST(:date AS date), 'SUPPLIER_BILL', :bill, 'ISSUE', 'Test bill')
                        """)
                .param("number", unique("JV/BT/") + System.nanoTime())
                .param("date", today())
                .param("bill", bill.id())
                .update();

        assertThatThrownBy(() -> bills.update(
                        bill.id(),
                        form(party, bill.billNo(), List.of(expenseLine(expenseAccount(), "300"))),
                        null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("in the books");
    }

    @Test
    void tdsSuggestedOnlyAboveThreshold() {
        long section = tdsSection("194C-O");
        long party = supplier("ABCDE1234F", section);
        long account = expenseAccount();

        var small = bills.suggestTds(party, today(), new BigDecimal("20000"), null);

        assertThat(small.sectionCode()).isEqualTo("194C-O");
        assertThat(small.ratePercent()).isEqualByComparingTo("2");
        assertThat(small.thresholdCrossed()).isFalse();
        assertThat(small.suggestedRupees()).isEqualByComparingTo("0");

        bills.create(form(party, unique("BILL-"), List.of(expenseLine(account, "20000"))), null);
        var big = bills.suggestTds(party, today(), new BigDecimal("90000"), null);

        assertThat(big.thresholdCrossed()).isTrue();
        assertThat(big.suggestedRupees()).isEqualByComparingTo("1800.00");
        assertThat(big.warning()).contains("194C-O");

        long noPan = supplier(null, section);
        var withoutPan = bills.suggestTds(noPan, today(), new BigDecimal("90000"), null);

        assertThat(withoutPan.ratePercent()).isEqualByComparingTo("20");
        assertThat(withoutPan.suggestedRupees()).isEqualByComparingTo("18000.00");
    }

    @Test
    void warningWhenCrossedAndNoTds() {
        long section = tdsSection("194C-O");
        long party = supplier("ABCDE1234F", section);
        long account = expenseAccount();
        bills.create(form(party, unique("BILL-"), List.of(expenseLine(account, "20000"))), null);

        var bill = bills.create(form(party, unique("BILL-"), List.of(expenseLine(account, "90000"))), null);

        assertThat(bill.tdsRupees()).isEqualByComparingTo("0");
        assertThat(bill.tdsWarning()).contains("TDS under 194C-O is due");
    }
}

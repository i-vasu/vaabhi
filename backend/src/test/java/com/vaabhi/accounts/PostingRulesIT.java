package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** What each posting rule books, checked on the entries of the sources the test creates. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class PostingRulesIT extends BooksFixtures {

    private static BigDecimal rupees(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    void shopAdvanceThenInvoiceMovesToDebtors() {
        long order = shopOrder("1050");
        long payment = shopPayment(order, "500", "CASH", base.minusMinutes(10));
        long invoice = shopInvoice(order, base.minusMinutes(5));

        sweeper.sweepNow();

        var key = "SHOP:" + order;
        assertThat(entry("ORDER_PAYMENT", payment, "RECEIPT").orderKey()).isEqualTo(key);
        entry("INVOICE", invoice, "ISSUE");
        entry("INVOICE", invoice, "APPLY");
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("550"));
        assertThat(net("CUSTOMER_ADVANCES", key)).isEqualByComparingTo(rupees("0"));
        assertThat(entry("ORDER_PAYMENT", payment, "RECEIPT").narration()).startsWith("Payment on ORD-BT-");
    }

    @Test
    void paymentAfterInvoiceCreditsDebtors() {
        long order = shopOrder("1050");
        long invoice = shopInvoice(order, base.minusMinutes(10));
        long payment = shopPayment(order, "1050", "UPI", base.minusMinutes(1));

        sweeper.sweepNow();

        var key = "SHOP:" + order;
        entry("INVOICE", invoice, "ISSUE");
        entry("ORDER_PAYMENT", payment, "RECEIPT");
        assertThat(entries("INVOICE", invoice)).extracting(EntryRow::step).doesNotContain("APPLY");
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("0"));
        assertThat(net("CUSTOMER_ADVANCES", key)).isEqualByComparingTo(rupees("0"));
    }

    @Test
    void webOnlineOrderReceiptAndInvoice() {
        long order = webOrder("ONLINE", "1050", base.minusMinutes(10), null);
        long invoice = webInvoice(order, base.minusMinutes(5));

        sweeper.sweepNow();

        var key = "WEB:" + order;
        var receipt = entry("WEB_ORDER", order, "RECEIPT");
        assertThat(netInEntry(receipt.id(), settings.accountFor("RAZORPAY"))).isEqualByComparingTo(rupees("1050"));
        entry("INVOICE", invoice, "ISSUE");
        entry("INVOICE", invoice, "APPLY");
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("0"));
        assertThat(net("CUSTOMER_ADVANCES", key)).isEqualByComparingTo(rupees("0"));
    }

    @Test
    void codOrderPaidAtDelivery() {
        long order = webOrder("COD", "1050", base.minusMinutes(1), null);
        long invoice = webInvoice(order, base.minusMinutes(10));

        sweeper.sweepNow();

        var key = "WEB:" + order;
        var receipt = entry("WEB_ORDER", order, "RECEIPT");
        assertThat(netInEntry(receipt.id(), settings.accountFor("COD"))).isEqualByComparingTo(rupees("1050"));
        entry("INVOICE", invoice, "ISSUE");
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("0"));
        assertThat(net("CUSTOMER_ADVANCES", key)).isEqualByComparingTo(rupees("0"));
    }

    @Test
    void webCancelBeforeShipRefundsFromAdvances() {
        long order = webOrder("ONLINE", "1600", base.minusMinutes(10), base.minusMinutes(1));

        sweeper.sweepNow();

        var key = "WEB:" + order;
        entry("WEB_ORDER", order, "RECEIPT");
        var refund = entry("WEB_ORDER", order, "REFUND");
        assertThat(netInEntry(refund.id(), settings.accountFor("RAZORPAY"))).isEqualByComparingTo(rupees("-1600"));
        assertThat(net("CUSTOMER_ADVANCES", key)).isEqualByComparingTo(rupees("0"));
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("0"));
    }

    @Test
    void creditNoteEntry() {
        long order = webOrder("COD", "1050", null, null);
        long invoice = webInvoice(order, base.minusMinutes(5));
        long note = creditNote(invoice);

        sweeper.sweepNow();

        var key = "WEB:" + order;
        var entry = entry("CREDIT_NOTE", note, "ISSUE");
        assertThat(entry.narration()).startsWith("Credit note CN/BT/");
        assertThat(netInEntry(entry.id(), accountId("SALES_RETURNS"))).isEqualByComparingTo(rupees("1000"));
        assertThat(netInEntry(entry.id(), accountId("OUTPUT_CGST"))).isEqualByComparingTo(rupees("25"));
        assertThat(netInEntry(entry.id(), accountId("OUTPUT_SGST"))).isEqualByComparingTo(rupees("25"));
        assertThat(netInEntry(entry.id(), accountId("DEBTORS"))).isEqualByComparingTo(rupees("-1050"));
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("0"));
    }

    @Test
    void invoiceCancelReversesIssueAndApply() {
        long order = shopOrder("1050");
        shopPayment(order, "500", "CASH", base.minusMinutes(10));
        long invoice = shopInvoice(order, base.minusMinutes(5));
        sweeper.sweepNow();
        var key = "SHOP:" + order;
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("550"));

        cancelInvoice(invoice);
        sweeper.sweepNow();

        entry("INVOICE", invoice, "CANCEL");
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("0"));
        assertThat(net("CUSTOMER_ADVANCES", key)).isEqualByComparingTo(rupees("-500"));
    }

    @Test
    void refundBeforeInvoiceReducesApply() {
        long order = shopOrder("1050");
        shopPayment(order, "500", "CASH", base.minusMinutes(20));
        long refund = shopPayment(order, "-200", "CASH", base.minusMinutes(15));
        long invoice = shopInvoice(order, base.minusMinutes(5));

        sweeper.sweepNow();

        var key = "SHOP:" + order;
        entry("ORDER_PAYMENT", refund, "REFUND");
        var apply = entry("INVOICE", invoice, "APPLY");
        assertThat(netInEntry(apply.id(), accountId("CUSTOMER_ADVANCES"))).isEqualByComparingTo(rupees("300"));
        assertThat(net("DEBTORS", key)).isEqualByComparingTo(rupees("750"));
        assertThat(net("CUSTOMER_ADVANCES", key)).isEqualByComparingTo(rupees("0"));
    }

    @Test
    void expensePostsToCategoryAccount() {
        long expense = expense("RENT", "1200", "UPI");

        sweeper.sweepNow();

        var entry = entry("EXPENSE", expense, "EXPENSE");
        assertThat(entry.narration()).isEqualTo("Expense: Rent");
        assertThat(netInEntry(entry.id(), accountId("EXPENSE_RENT"))).isEqualByComparingTo(rupees("1200"));
        assertThat(netInEntry(entry.id(), settings.accountFor("UPI"))).isEqualByComparingTo(rupees("-1200"));
    }

    @Test
    void razorpaySettlementPosts() {
        long seq = jdbc.sql("""
                        INSERT INTO razorpay_settlement (id, utr, settled_on, gross_rupees, fee_rupees, tax_rupees,
                                                         net_rupees)
                        VALUES (:id, NULL, CAST(now() AT TIME ZONE 'Asia/Kolkata' AS date),
                                10000, 200, 36, 9764)
                        RETURNING seq
                        """)
                .param("id", unique("setl_"))
                .query(Long.class)
                .single();

        sweeper.sweepNow();

        var entry = entry("RAZORPAY_SETTLEMENT", seq, "SETTLE");
        assertThat(netInEntry(entry.id(), accountId("BANK"))).isEqualByComparingTo(rupees("9764"));
        assertThat(netInEntry(entry.id(), accountId("EXPENSE_GATEWAY_FEES"))).isEqualByComparingTo(rupees("200"));
        assertThat(netInEntry(entry.id(), accountId("INPUT_GST"))).isEqualByComparingTo(rupees("36"));
        assertThat(netInEntry(entry.id(), accountId("RAZORPAY_CLEARING"))).isEqualByComparingTo(rupees("-10000"));
    }

    @Test
    void alterationReceipt() {
        long payment = alterationPayment("300", "CASH");

        sweeper.sweepNow();

        var entry = entry("ALTERATION_PAYMENT", payment, "RECEIPT");
        assertThat(netInEntry(entry.id(), settings.accountFor("CASH"))).isEqualByComparingTo(rupees("300"));
        assertThat(netInEntry(entry.id(), accountId("ALTERATION_INCOME"))).isEqualByComparingTo(rupees("-300"));
    }
}

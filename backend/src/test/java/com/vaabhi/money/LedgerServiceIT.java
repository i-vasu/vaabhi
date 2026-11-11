package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.tailoring.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Web payments and refunds in the ledger, against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class LedgerServiceIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(9_000);
    private static final LocalDate FROM = LocalDate.of(2003, 1, 1);
    private static final LocalDate TO = LocalDate.of(2003, 1, 31);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private LedgerService ledger;

    private long customer() {
        return jdbc.sql("INSERT INTO customer (name, phone) VALUES ('Test', :phone) RETURNING id")
                .param("phone", "+9198763" + "%05d".formatted(SEQUENCE.incrementAndGet()))
                .query(Long.class)
                .single();
    }

    private record Order(long id, String number) {}

    private Order order(String status, String method, String paidAt, String refundedAt, String refundStatus) {
        var number = "WEB-LED-" + SEQUENCE.incrementAndGet();
        long id = jdbc.sql("""
                        INSERT INTO web_order (number, customer_id, status, payment_method, contact_name,
                            contact_phone, address_line1, city, state, pincode, items_rupees, shipping_rupees,
                            total_rupees, paid_at, razorpay_payment_id, refunded_rupees, refunded_at, refund_status)
                        VALUES (:number, :customer, :status, :method, 'Ledger Buyer', '+919876200000', '1 Street',
                            'Pune', 'Maharashtra', '411001', 1500, 100, 1600,
                            CAST(:paidAt AS timestamptz),
                            CASE WHEN CAST(:method AS text) = 'ONLINE' AND CAST(:paidAt AS text) IS NOT NULL
                                 THEN CAST(:number AS text) END,
                            CASE WHEN CAST(:refundedAt AS text) IS NOT NULL THEN 1600 END,
                            CAST(:refundedAt AS timestamptz), :refundStatus)
                        RETURNING id
                        """)
                .param("number", number)
                .param("customer", customer())
                .param("status", status)
                .param("method", method)
                .param("paidAt", paidAt)
                .param("refundedAt", refundedAt)
                .param("refundStatus", refundStatus)
                .query(Long.class)
                .single();
        return new Order(id, number);
    }

    private List<LedgerEntry> entriesFor(String reference) {
        return ledger.ledger(FROM, TO, null).entries().stream()
                .filter(e -> e.reference().equals(reference))
                .toList();
    }

    @Test
    void onlinePaidOrderIsRazorpayRow() {
        var order = order("PLACED", "ONLINE", "2003-01-10T10:00:00Z", null, null);

        var rows = entriesFor(order.number());

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().kind()).isEqualTo(LedgerEntry.Kind.WEB_PAYMENT);
        assertThat(rows.getFirst().mode()).isEqualTo(PaymentMode.RAZORPAY);
        assertThat(rows.getFirst().amountRupees()).isEqualByComparingTo(new BigDecimal("1600"));
        assertThat(rows.getFirst().party()).isEqualTo("Ledger Buyer");
        assertThat(rows.getFirst().sourceId()).isEqualTo(order.id());
    }

    @Test
    void codDeliveredIsCodRow() {
        var order = order("DELIVERED", "COD", "2003-01-11T10:00:00Z", null, null);

        var rows = entriesFor(order.number());

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().kind()).isEqualTo(LedgerEntry.Kind.WEB_PAYMENT);
        assertThat(rows.getFirst().mode()).isEqualTo(PaymentMode.COD);
    }

    @Test
    void processedRefundIsNegativeRow() {
        var order = order("CANCELLED", "ONLINE", "2003-01-12T10:00:00Z", "2003-01-13T10:00:00Z", "PROCESSED");

        var rows = entriesFor(order.number());

        assertThat(rows).hasSize(2);
        var refund = rows.stream()
                .filter(e -> e.kind() == LedgerEntry.Kind.WEB_REFUND)
                .findFirst()
                .orElseThrow();
        assertThat(refund.mode()).isEqualTo(PaymentMode.RAZORPAY);
        assertThat(refund.amountRupees()).isEqualByComparingTo(new BigDecimal("-1600"));
    }

    @Test
    void manualReturnRefundIsBankRow() {
        var order = order("DELIVERED", "COD", "2003-01-14T10:00:00Z", null, null);
        var returnNumber = "RET-LED-" + SEQUENCE.incrementAndGet();
        long returnId = jdbc.sql("""
                        INSERT INTO web_order_return (order_id, number, status, reason, items_rupees, refund_rupees,
                            refund_method, refunded_at)
                        VALUES (:order, :number, 'REFUNDED', 'Too small', 500, 500, 'MANUAL',
                            CAST('2003-01-15T10:00:00Z' AS timestamptz))
                        RETURNING id
                        """)
                .param("order", order.id())
                .param("number", returnNumber)
                .query(Long.class)
                .single();

        var rows = entriesFor(returnNumber);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().kind()).isEqualTo(LedgerEntry.Kind.WEB_REFUND);
        assertThat(rows.getFirst().mode()).isEqualTo(PaymentMode.BANK);
        assertThat(rows.getFirst().amountRupees()).isEqualByComparingTo(new BigDecimal("-500"));
        assertThat(rows.getFirst().sourceId()).isEqualTo(returnId);
        assertThat(rows.getFirst().party()).isEqualTo("Ledger Buyer");
    }

    @Test
    void pendingOnlineOrderNotInLedger() {
        var order = order("PENDING_PAYMENT", "ONLINE", null, null, null);

        assertThat(entriesFor(order.number())).isEmpty();
    }
}

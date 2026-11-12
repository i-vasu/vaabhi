package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Credit notes against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class CreditNoteServiceIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(9_000);

    private static final String GST_ON = """
            {"chargeGst":true,"gstin":"36ABCDE1234F1Z5","defaultGstRate":5,"pricesIncludeTax":true,
             "showHsnCodes":true,"discountAppliesTo":"ORDER_TOTAL","autoInvoice":true,
             "company":{"legalName":"Vaabhi Test Pvt Ltd","state":"Telangana"},"defaultTaxClassId":%d}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private WebInvoiceService webInvoices;

    @Autowired
    private InvoiceService invoices;

    @Autowired
    private CreditNoteService creditNotes;

    @BeforeEach
    void gstOn() throws Exception {
        long garments = jdbc.sql("SELECT id FROM tax_class WHERE name = 'Garments'")
                .query(Long.class)
                .single();
        mockMvc.perform(put("/api/v1/admin/settings/billing")
                        .with(jwt().jwt(j -> j.subject("staff:1").claim("name", "Asha Rao"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                                        new SimpleGrantedAuthority("ROLE_OWNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GST_ON.formatted(garments)))
                .andExpect(status().isOk());
    }

    private long customer() {
        return jdbc.sql("INSERT INTO customer (name, phone) VALUES ('Test', :phone) RETURNING id")
                .param("phone", "+9198763" + "%05d".formatted(SEQUENCE.incrementAndGet()))
                .query(Long.class)
                .single();
    }

    private record Order(long id, String number) {}

    private Order order() {
        var number = "WEB-CN-" + SEQUENCE.incrementAndGet();
        long id = jdbc.sql("""
                        INSERT INTO web_order (number, customer_id, status, payment_method, contact_name,
                            contact_phone, address_line1, city, state, pincode, items_rupees, shipping_rupees,
                            total_rupees)
                        VALUES (:number, :customer, 'PLACED', 'COD', 'Test', '+919876200000', '1 Street', 'Pune',
                            'Maharashtra', '411001', 1500, 100, 1600)
                        RETURNING id
                        """)
                .param("number", number)
                .param("customer", customer())
                .query(Long.class)
                .single();
        return new Order(id, number);
    }

    private long orderLine(Order order, int quantity, String unit) {
        return jdbc.sql("""
                        INSERT INTO web_order_line (order_id, position, sku, product_slug, name, quantity,
                            unit_rupees, amount_rupees)
                        VALUES (:order, 1, :sku, 'kurta', 'Kurta', :quantity, :unit, :unit * :quantity)
                        RETURNING id
                        """)
                .param("order", order.id())
                .param("sku", "SKU-CN-" + SEQUENCE.incrementAndGet())
                .param("quantity", quantity)
                .param("unit", new BigDecimal(unit))
                .query(Long.class)
                .single();
    }

    private Invoice invoice(Order order, String shipping, WebInvoiceRequest.Line... lines) {
        return webInvoices.issueFor(new WebInvoiceRequest(
                order.id(),
                order.number(),
                null,
                new InvoiceParties.Buyer("Test", "+919876200000", "1 Street, Pune, 411001", "Maharashtra"),
                "36",
                List.of(lines),
                new BigDecimal(shipping)));
    }

    private Invoice twoLineInvoice(Order order) {
        return invoice(
                order,
                "100",
                new WebInvoiceRequest.Line("Silk saree (Teal)", 1, new BigDecimal("1000"), null, null),
                new WebInvoiceRequest.Line("Blouse", 1, new BigDecimal("500"), null, null));
    }

    @Test
    void fullNoteMirrorsInvoice() {
        var invoice = twoLineInvoice(order());

        var note = creditNotes.issueFull(invoice.id(), CreditNote.Reason.CORRECTION, "Wrong size", "staff:1");

        assertThat(note.number()).matches("CN/\\d{2}-\\d{2}/\\d{4}");
        assertThat(note.title()).isEqualTo("Credit Note");
        assertThat(note.invoiceId()).isEqualTo(invoice.id());
        assertThat(note.lines()).hasSize(invoice.lines().size());
        assertThat(note.taxableRupees()).isEqualByComparingTo(invoice.taxableRupees());
        assertThat(note.cgstRupees()).isEqualByComparingTo(invoice.cgstRupees());
        assertThat(note.sgstRupees()).isEqualByComparingTo(invoice.sgstRupees());
        assertThat(note.igstRupees()).isEqualByComparingTo(invoice.igstRupees());
        assertThat(note.totalRupees()).isEqualByComparingTo(invoice.totalRupees());
        assertThat(note.buyer()).isEqualTo(invoice.buyer());
        assertThat(invoices.get(invoice.id()).creditNotes())
                .extracting(CreditNoteSummary::id)
                .containsExactly(note.id());
        assertThatThrownBy(() -> creditNotes.issueFull(invoice.id(), CreditNote.Reason.CORRECTION, null, "staff:1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already fully credited");
    }

    @Test
    void partialNoteCreditsChosenLines() {
        var invoice = twoLineInvoice(order());
        var first = invoice.lines().get(0);

        var note = creditNotes.issuePartial(
                invoice.id(),
                List.of(new CreditNoteForm.Line(first.id(), 1)),
                CreditNote.Reason.RETURN,
                null,
                "staff:1");

        assertThat(note.lines()).hasSize(1);
        assertThat(note.lines().get(0).invoiceLineId()).isEqualTo(first.id());
        assertThat(note.taxableRupees()).isEqualByComparingTo(first.taxableRupees());
        assertThat(note.totalRupees())
                .isEqualByComparingTo(first.taxableRupees()
                        .add(first.cgstRupees())
                        .add(first.sgstRupees())
                        .add(first.igstRupees()));
        assertThat(note.totalRupees()).isLessThan(invoice.totalRupees());
    }

    @Test
    void secondPartialCreditsOnlyRemainder() {
        var invoice = invoice(order(), "0", new WebInvoiceRequest.Line("Kurta", 3, new BigDecimal("700"), null, null));
        var line = invoice.lines().get(0);

        var first = creditNotes.issuePartial(
                invoice.id(),
                List.of(new CreditNoteForm.Line(line.id(), 1)),
                CreditNote.Reason.RETURN,
                null,
                "staff:1");
        var second = creditNotes.issuePartial(
                invoice.id(),
                List.of(new CreditNoteForm.Line(line.id(), 2)),
                CreditNote.Reason.RETURN,
                null,
                "staff:1");

        var a = first.lines().get(0);
        var b = second.lines().get(0);
        assertThat(b.taxableRupees()).isEqualByComparingTo(line.taxableRupees().subtract(a.taxableRupees()));
        assertThat(b.cgstRupees()).isEqualByComparingTo(line.cgstRupees().subtract(a.cgstRupees()));
        assertThat(b.sgstRupees()).isEqualByComparingTo(line.sgstRupees().subtract(a.sgstRupees()));
        assertThat(b.igstRupees()).isEqualByComparingTo(line.igstRupees().subtract(a.igstRupees()));
        assertThat(first.totalRupees().add(second.totalRupees())).isEqualByComparingTo(invoice.totalRupees());
        assertThatThrownBy(() -> creditNotes.issuePartial(
                        invoice.id(),
                        List.of(new CreditNoteForm.Line(line.id(), 1)),
                        CreditNote.Reason.RETURN,
                        null,
                        "staff:1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void cancelledInvoiceRefused() {
        var invoice = twoLineInvoice(order());
        invoices.cancel(invoice.id(), "staff:1");

        assertThatThrownBy(() -> creditNotes.issueFull(invoice.id(), CreditNote.Reason.CORRECTION, null, "staff:1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void returnNoteIsIdempotent() {
        var order = order();
        long lineId = orderLine(order, 2, "700");
        var invoice = invoice(order, "0", new WebInvoiceRequest.Line("Kurta", 2, new BigDecimal("700"), null, lineId));
        long returnId = jdbc.sql("""
                        INSERT INTO web_order_return (order_id, number, reason, items_rupees)
                        VALUES (:order, :number, 'Too small', 700)
                        RETURNING id
                        """)
                .param("order", order.id())
                .param("number", order.number() + "-R1")
                .query(Long.class)
                .single();

        var first = creditNotes.issueForReturn(returnId, order.id(), Map.of(lineId, 1), "staff:1");
        var second = creditNotes.issueForReturn(returnId, order.id(), Map.of(lineId, 1), "staff:1");

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.reason()).isEqualTo(CreditNote.Reason.RETURN);
        assertThat(first.lines().get(0).quantity()).isEqualTo(1);
        assertThat(invoices.get(invoice.id()).creditNotes()).hasSize(1);
    }

    @Test
    void returnOnOrderWithoutInvoiceDoesNothing() {
        var order = order();
        long lineId = orderLine(order, 1, "700");
        long returnId = jdbc.sql("""
                        INSERT INTO web_order_return (order_id, number, reason, items_rupees)
                        VALUES (:order, :number, 'Too small', 700)
                        RETURNING id
                        """)
                .param("order", order.id())
                .param("number", order.number() + "-R1")
                .query(Long.class)
                .single();

        assertThat(creditNotes.issueForReturn(returnId, order.id(), Map.of(lineId, 1), null))
                .isNull();
    }

    @Test
    void cancelRefusedAfterIssueDay() {
        var invoice = twoLineInvoice(order());
        jdbc.sql("UPDATE invoice SET issued_on = issued_on - 1 WHERE id = :id")
                .param("id", invoice.id())
                .update();

        assertThatThrownBy(() -> invoices.cancel(invoice.id(), "staff:1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Issue a credit note instead");
    }

    @Test
    void webCancelIssuesFullNoteNotCancel() {
        var order = order();
        var invoice = twoLineInvoice(order);

        webInvoices.creditFor(order.id(), null);
        webInvoices.creditFor(order.id(), null);

        var after = invoices.get(invoice.id());
        assertThat(after.cancelledAt()).isNull();
        assertThat(after.creditNotes()).hasSize(1);
        assertThat(after.creditNotes().get(0).reason()).isEqualTo(CreditNote.Reason.CANCELLED);
        assertThat(after.creditNotes().get(0).totalRupees()).isEqualByComparingTo(invoice.totalRupees());
    }
}

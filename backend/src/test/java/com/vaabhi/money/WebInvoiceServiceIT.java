package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.util.List;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/** Invoices for web orders against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class WebInvoiceServiceIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(8_000);

    private static final String BILLING = """
            {"chargeGst":false,"gstin":null,"defaultGstRate":5,"pricesIncludeTax":true,"showHsnCodes":false,
             "discountAppliesTo":"ORDER_TOTAL","autoInvoice":true,
             "company":{"legalName":"Vaabhi Test Pvt Ltd","state":"Telangana"},"defaultTaxClassId":%d}
            """;

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

    @BeforeEach
    void billing() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings/billing")
                        .with(jwt().jwt(j -> j.subject("staff:1").claim("name", "Asha Rao"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                                        new SimpleGrantedAuthority("ROLE_OWNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BILLING.formatted(jdbc.sql("SELECT id FROM tax_class WHERE name = 'Garments'")
                                .query(Long.class)
                                .single())))
                .andExpect(status().isOk());
    }

    private long customer() {
        return jdbc.sql("INSERT INTO customer (name, phone) VALUES ('Test', :phone) RETURNING id")
                .param("phone", "+9198762" + "%05d".formatted(SEQUENCE.incrementAndGet()))
                .query(Long.class)
                .single();
    }

    private record Order(long id, String number) {}

    private Order order(long customerId) {
        var number = "WEB-IT-" + SEQUENCE.incrementAndGet();
        long id = jdbc.sql("""
                        INSERT INTO web_order (number, customer_id, status, payment_method, contact_name,
                            contact_phone, address_line1, city, state, pincode, items_rupees, shipping_rupees,
                            total_rupees)
                        VALUES (:number, :customer, 'PLACED', 'COD', 'Test', '+919876200000', '1 Street', 'Pune',
                            'Maharashtra', '411001', 1500, 100, 1600)
                        RETURNING id
                        """)
                .param("number", number)
                .param("customer", customerId)
                .query(Long.class)
                .single();
        return new Order(id, number);
    }

    private static WebInvoiceRequest request(Order order) {
        return request(
                order,
                "27",
                "100",
                new WebInvoiceRequest.Line("Silk saree (Teal)", 1, new BigDecimal("1000"), null, null),
                new WebInvoiceRequest.Line("Blouse", 1, new BigDecimal("500"), null, null));
    }

    private static WebInvoiceRequest request(
            Order order, String buyerStateCode, String shipping, WebInvoiceRequest.Line... lines) {
        return new WebInvoiceRequest(
                order.id(),
                order.number(),
                null,
                new InvoiceParties.Buyer("Test", "+919876200000", "1 Street, Pune, 411001", "Maharashtra"),
                buyerStateCode,
                List.of(lines),
                new BigDecimal(shipping));
    }

    /** Switches GST on for the seller in Telangana (36), with Garments as the default tax class. */
    private void gstOn() throws Exception {
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

    @Test
    void outOfStateOrderIsIgst() throws Exception {
        gstOn();
        var order = order(customer());

        var invoice = webInvoices.issueFor(request(
                order,
                "29",
                "100",
                new WebInvoiceRequest.Line("Silk saree (Teal)", 1, new BigDecimal("1000"), null, null),
                new WebInvoiceRequest.Line("Blouse", 1, new BigDecimal("500"), null, null)));

        assertThat(invoice.supplyType()).isEqualTo("INTER");
        assertThat(invoice.placeOfSupply()).isEqualTo("29");
        assertThat(invoice.igstRupees()).isGreaterThan(BigDecimal.ZERO);
        assertThat(invoice.cgstRupees()).isEqualByComparingTo("0");
        assertThat(invoice.sgstRupees()).isEqualByComparingTo("0");
        assertThat(invoice.totalRupees()).isEqualByComparingTo("1600");
    }

    @Test
    void sameStateOrderIsCgstAndSgst() throws Exception {
        gstOn();
        var order = order(customer());

        var invoice = webInvoices.issueFor(request(
                order,
                "36",
                "0",
                new WebInvoiceRequest.Line("Silk saree (Teal)", 1, new BigDecimal("1000"), null, null)));

        assertThat(invoice.supplyType()).isEqualTo("INTRA");
        assertThat(invoice.igstRupees()).isEqualByComparingTo("0");
        assertThat(invoice.cgstRupees()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void slabPerLine() throws Exception {
        gstOn();
        var order = order(customer());

        var invoice = webInvoices.issueFor(request(
                order,
                "36",
                "0",
                new WebInvoiceRequest.Line("Banarasi saree", 1, new BigDecimal("2800"), null, null),
                new WebInvoiceRequest.Line("Cotton saree", 1, new BigDecimal("2000"), null, null)));

        assertThat(invoice.lines().get(0).gstRate()).isEqualByComparingTo("18");
        assertThat(invoice.lines().get(1).gstRate()).isEqualByComparingTo("5");
        assertThat(invoice.gstRate()).isEqualByComparingTo("18");
    }

    @Test
    void gstOffInvoiceHasHsnButNoTax() {
        var order = order(customer());

        var invoice = webInvoices.issueFor(request(
                order,
                "29",
                "100",
                new WebInvoiceRequest.Line("Silk saree (Teal)", 1, new BigDecimal("1000"), null, null)));

        assertThat(invoice.lines().get(0).hsnCode()).isEqualTo("6204");
        assertThat(invoice.lines().get(0).gstRate()).isEqualByComparingTo("0");
        assertThat(invoice.cgstRupees()).isEqualByComparingTo("0");
        assertThat(invoice.sgstRupees()).isEqualByComparingTo("0");
        assertThat(invoice.igstRupees()).isEqualByComparingTo("0");
        assertThat(invoice.supplyType()).isEqualTo("INTER");
        assertThat(invoice.taxableRupees()).isEqualByComparingTo(invoice.totalRupees());
    }

    @Test
    void linesSumToInvoice() throws Exception {
        gstOn();
        var order = order(customer());

        var invoice = webInvoices.issueFor(request(
                order,
                "29",
                "100",
                new WebInvoiceRequest.Line("Silk saree (Teal)", 1, new BigDecimal("2800"), null, null),
                new WebInvoiceRequest.Line("Blouse", 3, new BigDecimal("333.33"), null, null)));

        assertThat(Rupees.sum(invoice.lines(), InvoiceLine::taxableRupees))
                .isEqualByComparingTo(invoice.taxableRupees());
        assertThat(Rupees.sum(invoice.lines(), InvoiceLine::cgstRupees)).isEqualByComparingTo(invoice.cgstRupees());
        assertThat(Rupees.sum(invoice.lines(), InvoiceLine::sgstRupees)).isEqualByComparingTo(invoice.sgstRupees());
        assertThat(Rupees.sum(invoice.lines(), InvoiceLine::igstRupees)).isEqualByComparingTo(invoice.igstRupees());
    }

    @Test
    void issueForCreatesInvoice() {
        var order = order(customer());

        var invoice = webInvoices.issueFor(request(order));

        assertThat(invoice.number()).startsWith("INV/");
        assertThat(invoice.source()).isEqualTo(Invoice.Source.WEB);
        assertThat(invoice.orderNumber()).isEqualTo(order.number());
        assertThat(invoice.lines()).hasSize(3);
        assertThat(invoice.lines().get(2).description()).isEqualTo("Delivery");
        assertThat(invoice.totalRupees()).isEqualByComparingTo("1600");
    }

    @Test
    void issueForReturnsLiveInvoice() {
        var order = order(customer());

        var first = webInvoices.issueFor(request(order));
        var second = webInvoices.issueFor(request(order));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(webInvoices.liveNumbers(List.of(order.id()))).containsEntry(order.id(), first.number());
    }

    @Test
    void cancelForThenIssueGivesNewNumber() {
        var order = order(customer());
        var first = webInvoices.issueFor(request(order));

        webInvoices.cancelFor(order.id(), "staff:1");
        var second = webInvoices.issueFor(request(order));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.number()).isNotEqualTo(first.number());
    }

    @Test
    void forCustomerOtherCustomerEmpty() {
        var owner = customer();
        var order = order(owner);
        webInvoices.issueFor(request(order));

        assertThat(webInvoices.forCustomer(order.number(), owner)).isPresent();
        assertThat(webInvoices.forCustomer(order.number(), customer())).isEmpty();
    }
}

package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
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
             "discountAppliesTo":"ORDER_TOTAL","autoInvoice":true}
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
                        .content(BILLING))
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
        return new WebInvoiceRequest(
                order.id(),
                order.number(),
                null,
                new InvoiceParties.Buyer("Test", "+919876200000", "1 Street, Pune, 411001", "Maharashtra"),
                List.of(
                        new WebInvoiceRequest.Line("Silk saree (Teal)", 1, new BigDecimal("1000")),
                        new WebInvoiceRequest.Line("Blouse", 1, new BigDecimal("500"))),
                new BigDecimal("100"));
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

package com.vaabhi.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Web orders get an invoice when they ship, reuse it on a retry, cancel it with the order, and show it to the buyer. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class WebOrderInvoiceIT {

    private static final AtomicInteger PHONES = new AtomicInteger(30_000);

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Demo Owner"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CheckoutService checkoutService;

    private String sku;
    private RequestPostProcessor customer;

    @BeforeEach
    void setUp() {
        sku = jdbc.sql("""
                        SELECT v.sku FROM product_variant v JOIN product p ON p.id = v.product_id
                        WHERE p.status = 'PUBLISHED' AND v.active ORDER BY v.id LIMIT 1
                        """).query(String.class).single();
        jdbc.sql("UPDATE product_variant SET stock_qty = 10 WHERE sku = :sku")
                .param("sku", sku)
                .update();
        customer = newCustomer();
    }

    @Test
    void manualShipIssuesInvoice() throws Exception {
        String number = placeCodOrder(customer);
        mockMvc.perform(get("/api/v1/customer/orders/" + number + "/invoice").with(customer))
                .andExpect(status().isNotFound());

        ship(number).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/customer/orders/" + number + "/invoice").with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").isNotEmpty())
                .andExpect(jsonPath("$.orderNumber").value(number));
    }

    @Test
    void deliverFromPlacedIssuesInvoice() throws Exception {
        String number = placeCodOrder(customer);

        mockMvc.perform(post("/api/v1/admin/online-orders/" + number + "/deliver")
                        .with(OWNER))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/customer/orders/" + number + "/invoice").with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(number));
    }

    @Test
    void shipRetryReusesInvoice() throws Exception {
        String number = placeCodOrder(customer);
        var first = checkoutService.invoiceFor(number);
        var second = checkoutService.invoiceFor(number);
        assertThat(second.number()).isEqualTo(first.number());
        assertThat(second.id()).isEqualTo(first.id());

        ship(number).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customer/orders/" + number + "/invoice").with(customer))
                .andExpect(jsonPath("$.number").value(first.number()));
    }

    @Test
    void cancelBeforeShipWithInvoiceIssuesCreditNote() throws Exception {
        String number = placeCodOrder(customer);
        var invoice = checkoutService.invoiceFor(number);

        mockMvc.perform(post("/api/v1/customer/orders/" + number + "/cancel")
                        .with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(jdbc.sql("SELECT cancelled_at IS NOT NULL FROM invoice WHERE id = :id")
                        .param("id", invoice.id())
                        .query(Boolean.class)
                        .single())
                .isFalse();
        mockMvc.perform(get("/api/v1/customer/orders/" + number + "/credit-notes")
                        .with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reason").value("CANCELLED"))
                .andExpect(jsonPath("$[0].invoiceNumber").value(invoice.number()));
    }

    @Test
    void invoiceOfOtherCustomer404() throws Exception {
        String number = placeCodOrder(customer);
        ship(number).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/customer/orders/" + number + "/invoice").with(newCustomer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderListShowsInvoiceNumber() throws Exception {
        String shipped = placeCodOrder(customer);
        String waiting = placeCodOrder(customer);
        ship(shipped).andExpect(status().isOk());

        var list = mockMvc.perform(get("/api/v1/customer/orders").with(customer))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> withInvoice = JsonPath.read(list, "$[?(@.number == '" + shipped + "')].invoiceNumber");
        List<String> without = JsonPath.read(list, "$[?(@.number == '" + waiting + "')].invoiceNumber");
        assertThat(withInvoice).hasSize(1);
        assertThat(withInvoice.getFirst()).isNotBlank();
        assertThat(without).allMatch(n -> n == null);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private RequestPostProcessor newCustomer() {
        int n = PHONES.incrementAndGet();
        long id = jdbc.sql("""
                        INSERT INTO customer (name, email, phone) VALUES ('Meera', :email, :phone) RETURNING id
                        """)
                .param("email", "invoice" + n + "@vaabhi.test")
                .param("phone", "+9196" + n + "123")
                .query(Long.class)
                .single();
        return jwt().jwt(j -> j.subject(Long.toString(id))).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private String placeCodOrder(RequestPostProcessor buyer) throws Exception {
        var body = """
                {"lines":[{"sku":"%s","addonCode":null,"blouseSize":null,"quantity":1}],
                 "address":{"name":"Meera Iyer","phone":"9876501234","line1":"4 Lake View","line2":null,
                            "city":"Delhi","state":"Delhi","pincode":"110001"},
                 "paymentMethod":"COD"}
                """.formatted(sku);
        var result = mockMvc.perform(post("/api/v1/customer/checkout")
                        .with(buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.orderNumber");
    }

    private ResultActions ship(String number) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/online-orders/" + number + "/ship")
                .with(OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courier\":\"DTDC\",\"trackingNumber\":\"T123\"}"));
    }
}

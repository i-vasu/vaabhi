package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.rupees.Rupees;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shop invoices taxed per line, against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class InvoiceServiceIT {

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Asha Rao"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    private static final AtomicInteger PHONES = new AtomicInteger(6_000);

    private static final String GST_ON = """
            {"chargeGst":true,"gstin":"36ABCDE1234F1Z5","defaultGstRate":5,"pricesIncludeTax":true,
             "showHsnCodes":true,"discountAppliesTo":"ORDER_TOTAL","autoInvoice":false,
             "company":{"legalName":"Vaabhi Test Pvt Ltd","state":"Telangana"},"defaultTaxClassId":%d}
            """;

    private static final String GST_OFF = """
            {"chargeGst":false,"gstin":null,"defaultGstRate":5,"pricesIncludeTax":true,"showHsnCodes":false,
             "discountAppliesTo":"ORDER_TOTAL","autoInvoice":true,"defaultTaxClassId":%d}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private InvoiceService invoices;

    private long taxClass(String name) {
        return jdbc.sql("SELECT id FROM tax_class WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(
                request.with(OWNER).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void billing(String json) throws Exception {
        send(put("/api/v1/admin/settings/billing"), json.formatted(taxClass("Garments")))
                .andExpect(status().isOk());
    }

    @BeforeEach
    void gstOn() throws Exception {
        billing(GST_ON);
    }

    @AfterEach
    void gstOff() throws Exception {
        billing(GST_OFF);
    }

    private long client() throws Exception {
        var result = send(post("/api/v1/admin/clients"), """
                        {"phone":"98763%05d","firstName":"Ira","gender":"FEMALE","clientType":"RETAIL"}
                        """.formatted(PHONES.incrementAndGet()))
                .andExpect(status().isCreated());
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long order(String items, String discount) throws Exception {
        var today = LocalDate.now();
        var result = send(post("/api/v1/admin/orders"), """
                        {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[%s],
                         %s "redeemPoints":0,"advanceRupees":0,"advanceMode":"UPI"}
                        """.formatted(
                                client(), today, today.plusDays(5), items, discount))
                .andExpect(status().isCreated());
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void shopDiscountSpreadKeepsTotal() throws Exception {
        long orderId = order("""
                {"description":"Blouse","colour":"Teal","quantity":2,"priceRupees":500},
                {"description":"Fall & pico","quantity":1,"priceRupees":100}
                """, """
                "discountKind":"AMOUNT","discountValue":100,""");

        var invoice = invoices.issue(orderId, "staff:1");

        assertThat(invoice.totalRupees()).isEqualByComparingTo("1000");
        assertThat(invoice.orderTotalRupees()).isEqualByComparingTo("1000");
        assertThat(invoice.supplyType()).isEqualTo("INTRA");
        assertThat(Rupees.sum(invoice.lines(), InvoiceLine::discountRupees)).isEqualByComparingTo("100");
        assertThat(Rupees.sum(invoice.lines(), InvoiceLine::taxableRupees))
                .isEqualByComparingTo(invoice.taxableRupees());
        assertThat(invoice.taxableRupees()
                        .add(invoice.cgstRupees())
                        .add(invoice.sgstRupees())
                        .add(invoice.igstRupees()))
                .isEqualByComparingTo("1000");
    }

    @Test
    void shopItemUsesServiceProductClass() throws Exception {
        long serviceProduct = jdbc.sql("""
                        INSERT INTO service_product (template_id, code, name, price_paise, tax_class_id)
                        VALUES ((SELECT id FROM garment_template ORDER BY id LIMIT 1), :code, 'Stitching', 50000, :class)
                        RETURNING id
                        """)
                .param("code", "TAX-IT-" + PHONES.incrementAndGet())
                .param("class", taxClass("Tailoring service"))
                .query(Long.class)
                .single();
        long orderId = order("""
                {"description":"Stitching","serviceProductId":%d,"quantity":1,"priceRupees":500}
                """.formatted(serviceProduct), "\"discountKind\":\"AMOUNT\",\"discountValue\":0,");

        var invoice = invoices.issue(orderId, "staff:1");

        assertThat(invoice.lines()).hasSize(1);
        assertThat(invoice.lines().get(0).hsnCode()).isEqualTo("998821");
        assertThat(invoice.lines().get(0).gstRate()).isEqualByComparingTo("5");
    }
}

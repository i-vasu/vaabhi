package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Invoices, expenses, the ledger and dues against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class MoneyIT {

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Asha Rao"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    /** Every test makes its own customers; the database is shared by the whole test run. */
    private static final AtomicInteger PHONES = new AtomicInteger(7_000);

    private static final String DEFAULT_BILLING = """
            {"chargeGst":false,"gstin":null,"defaultGstRate":5,"pricesIncludeTax":true,"showHsnCodes":false,
             "discountAppliesTo":"ORDER_TOTAL","autoInvoice":true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(
                request.with(OWNER).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions read(String path, Map<String, String> params) throws Exception {
        var request = get("/api/v1/admin/money" + path).with(OWNER);
        params.forEach(request::param);
        return mockMvc.perform(request);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(body(result), path)).longValue();
    }

    private long client(String name) throws Exception {
        return id(
                send(post("/api/v1/admin/clients"), """
                                {"phone":"98761%05d","firstName":"%s","gender":"FEMALE","clientType":"RETAIL"}
                                """.formatted(PHONES.incrementAndGet(), name))
                        .andExpect(status().isCreated()),
                "$.id");
    }

    /** Books 2 × ₹500 and a ₹100 item with ₹100 off (total ₹1,000), paying an advance; returns the order id. */
    private long order(long clientId, long advanceRupees) throws Exception {
        var today = LocalDate.now();
        return id(
                send(post("/api/v1/admin/orders"), """
                                {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                                  {"description":"Blouse","colour":"Teal","quantity":2,"priceRupees":500},
                                  {"description":"Fall & pico","quantity":1,"priceRupees":100}],
                                 "discountKind":"AMOUNT","redeemPoints":0,"discountValue":100,"advanceRupees":%d,"advanceMode":"UPI"}
                                """.formatted(clientId, today, today.plusDays(5), advanceRupees))
                        .andExpect(status().isCreated()),
                "$.id");
    }

    /** SettingsIT saves other billing settings and the database is shared, so start from the defaults. */
    @BeforeEach
    void defaultBilling() throws Exception {
        send(put("/api/v1/admin/settings/billing"), DEFAULT_BILLING).andExpect(status().isOk());
    }

    @Test
    void moneyNeedsAnAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/money/invoices")).andExpect(status().isUnauthorized());
    }

    @Test
    void deliveringAnOrderIssuesItsInvoice() throws Exception {
        long orderId = order(client("Ira"), 200);
        mockMvc.perform(get("/api/v1/admin/money/orders/" + orderId + "/invoice")
                        .with(OWNER))
                .andExpect(status().isNoContent());

        send(post("/api/v1/admin/orders/" + orderId + "/deliver"), "").andExpect(status().isOk());
        var invoice = mockMvc.perform(get("/api/v1/admin/money/orders/" + orderId + "/invoice")
                        .with(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").isNotEmpty())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].description").value("Blouse (Teal)"))
                .andExpect(jsonPath("$.lines[0].amountRupees").value(1000))
                .andExpect(jsonPath("$.discountRupees").value(100))
                .andExpect(jsonPath("$.orderTotalRupees").value(1000))
                .andExpect(jsonPath("$.cgstRupees").value(0))
                .andExpect(jsonPath("$.totalRupees").value(1000))
                .andExpect(jsonPath("$.paidRupees").value(200))
                .andExpect(jsonPath("$.balanceRupees").value(800))
                .andExpect(jsonPath("$.createdBy").value("Asha Rao"));
        long invoiceId = id(invoice, "$.id");
        String number = JsonPath.read(body(invoice), "$.number");

        send(post("/api/v1/admin/money/orders/" + orderId + "/invoice"), "").andExpect(status().isConflict());
        read("/invoices", Map.of("q", number))
                .andExpect(jsonPath("$.items[0].id").value(invoiceId))
                .andExpect(jsonPath("$.items[0].balanceRupees").value(800));

        send(post("/api/v1/admin/money/invoices/" + invoiceId + "/cancel"), "")
                .andExpect(jsonPath("$.cancelledAt").exists())
                .andExpect(jsonPath("$.cancelledBy").value("Asha Rao"));
        send(post("/api/v1/admin/money/invoices/" + invoiceId + "/cancel"), "").andExpect(status().isBadRequest());
        send(post("/api/v1/admin/money/orders/" + orderId + "/invoice"), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.not(number)));
    }

    @Test
    void addsGstOnTopWhenPricesExcludeIt() throws Exception {
        try {
            send(put("/api/v1/admin/settings/billing"), """
                            {"chargeGst":true,"gstin":"27ABCDE1234F1Z5","defaultGstRate":12,"pricesIncludeTax":false,
                             "showHsnCodes":false,"discountAppliesTo":"ORDER_TOTAL","autoInvoice":false,
                             "defaultTaxClassId":%d}
                            """.formatted(
                                    jdbc.sql("SELECT id FROM tax_class WHERE name = 'Garments'")
                                            .query(Long.class)
                                            .single()))
                    .andExpect(status().isOk());
            long clientId = client("Jaya");
            long orderId = order(clientId, 0);
            send(post("/api/v1/admin/orders/" + orderId + "/deliver"), "").andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/admin/money/orders/" + orderId + "/invoice")
                            .with(OWNER))
                    .andExpect(status().isNoContent());

            send(post("/api/v1/admin/money/orders/" + orderId + "/invoice"), "")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.gstRate").value(5))
                    .andExpect(jsonPath("$.taxableRupees").value(1000))
                    .andExpect(jsonPath("$.igstRupees").value(0))
                    .andExpect(jsonPath("$.totalRupees").value(1050))
                    .andExpect(jsonPath("$.shopGstin").value("27ABCDE1234F1Z5"));

            // What is owed follows the invoice, GST included, and is flagged as already handed over.
            read("/outstanding", Map.of("clientId", Long.toString(clientId)))
                    .andExpect(jsonPath("$.totalRupees").value(1050))
                    .andExpect(jsonPath("$.handedOverRupees").value(1050))
                    .andExpect(jsonPath("$.items[0].kind").value("ORDER"))
                    .andExpect(jsonPath("$.items[0].invoiceNumber").isNotEmpty());
        } finally {
            send(put("/api/v1/admin/settings/billing"), DEFAULT_BILLING).andExpect(status().isOk());
        }
    }

    @Test
    void cancellingAnOrderVoidsItsInvoice() throws Exception {
        long orderId = order(client("Kiran"), 0);
        long invoiceId = id(
                send(post("/api/v1/admin/money/orders/" + orderId + "/invoice"), "")
                        .andExpect(status().isCreated()),
                "$.id");
        send(post("/api/v1/admin/orders/" + orderId + "/cancel"), "").andExpect(status().isOk());
        read("/invoices/" + invoiceId, Map.of())
                .andExpect(jsonPath("$.cancelledAt").exists());
        send(post("/api/v1/admin/money/orders/" + orderId + "/invoice"), "").andExpect(status().isBadRequest());
    }

    @Test
    void outstandingListsOrdersAndPaidAlterationsOldestFirst() throws Exception {
        long clientId = client("Lalita");
        long orderId = order(clientId, 300);
        var alteration = send(post("/api/v1/admin/alterations"), """
                        {"clientId":%d,"readyBy":"%s","paid":true,"undertakingAccepted":true,
                         "items":[{"kind":"REPAIR","garment":"Saree","costRupees":150}]}
                        """.formatted(
                                clientId, LocalDate.now().plusDays(1)))
                .andExpect(status().isCreated());
        long alterationId = id(alteration, "$.id");

        read("/outstanding", Map.of("clientId", Long.toString(clientId)))
                .andExpect(jsonPath("$.totalRupees").value(700 + 150))
                .andExpect(jsonPath("$.handedOverRupees").value(0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].kind").value("ALTERATION"))
                .andExpect(jsonPath("$.items[1].id").value(orderId))
                .andExpect(jsonPath("$.items[1].dueRupees").value(700));

        send(post("/api/v1/admin/alterations/" + alterationId + "/progress"), """
                        {"status":"DELIVERED","collectRupees":150,"mode":"UPI"}
                        """)
                .andExpect(status().isOk());
        read("/outstanding", Map.of("clientId", Long.toString(clientId)))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void ledgerListsPaymentsRefundsAndAlterationMoneyWithARunningBalance() throws Exception {
        long clientId = client("Mala");
        long orderId = order(clientId, 250);
        String orderNumber = JsonPath.read(
                body(mockMvc.perform(get("/api/v1/admin/orders/" + orderId).with(OWNER))), "$.number");
        send(post("/api/v1/admin/orders/" + orderId + "/payments"), """
                        {"amountRupees":50,"mode":"UPI","refund":true}
                        """).andExpect(status().isCreated());
        long alterationId = id(
                send(post("/api/v1/admin/alterations"), """
                                {"clientId":%d,"readyBy":"%s","paid":true,"undertakingAccepted":true,
                                 "items":[{"kind":"LENGTH","garment":"Kurti","costRupees":80}]}
                                """.formatted(clientId, LocalDate.now()))
                        .andExpect(status().isCreated()),
                "$.id");
        send(post("/api/v1/admin/alterations/" + alterationId + "/progress"), """
                        {"status":"DELIVERED","collectRupees":80,"mode":"CARD"}
                        """)
                .andExpect(status().isOk());

        var today = LocalDate.now().toString();
        var ledger = body(read("/ledger", Map.of("from", today, "to", today)).andExpect(status().isOk()));
        List<Map<String, Object>> mine = JsonPath.read(ledger, "$.entries[?(@.reference == '" + orderNumber + "')]");
        assertThat(mine).extracting(e -> e.get("kind")).containsExactly("ORDER_PAYMENT", "REFUND");
        assertThat(mine)
                .extracting(e -> ((Number) e.get("amountRupees")).longValue())
                .containsExactly(250L, -50L);
        List<Map<String, Object>> altered = JsonPath.read(
                ledger, "$.entries[?(@.sourceId == " + alterationId + " && @.kind == 'ALTERATION_PAYMENT')]");
        assertThat(altered)
                .singleElement()
                .satisfies(e -> assertThat(e.get("mode")).isEqualTo("CARD"));

        long opening = ((Number) JsonPath.read(ledger, "$.openingRupees")).longValue();
        long in = ((Number) JsonPath.read(ledger, "$.inRupees")).longValue();
        long out = ((Number) JsonPath.read(ledger, "$.outRupees")).longValue();
        long closing = ((Number) JsonPath.read(ledger, "$.closingRupees")).longValue();
        List<Number> balances = JsonPath.read(ledger, "$.entries[*].balanceRupees");
        assertThat(closing)
                .isEqualTo(opening + in - out)
                .isEqualTo(balances.getLast().longValue());

        read("/ledger", Map.of("from", today, "to", today, "kind", "REFUND"))
                .andExpect(jsonPath("$.entries[?(@.kind != 'REFUND')]").isEmpty());
        read("/ledger", Map.of("from", today, "to", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        read("/ledger", Map.of("from", "2020-01-01", "to", "2026-12-31")).andExpect(status().isBadRequest());
    }

    @Test
    void recordsEditsAndRemovesExpensesAndTheyReachTheLedger() throws Exception {
        // A month in the past that no other test writes to, so totals are exact.
        var created = send(post("/api/v1/admin/money/expenses"), """
                        {"spentOn":"2001-03-05","category":"RENT","description":"Shop rent","amountRupees":15000,
                         "mode":"BANK","paidTo":"Landlord"}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value("Asha Rao"));
        long rent = id(created, "$.id");
        send(post("/api/v1/admin/money/expenses"), """
                        {"spentOn":"2001-03-09","category":"MATERIAL","description":"Thread","amountRupees":200,
                         "mode":"CASH"}
                        """).andExpect(status().isCreated());
        send(post("/api/v1/admin/money/expenses"), """
                        {"spentOn":"2001-03-09","category":"MATERIAL","description":"","amountRupees":0,"mode":"CASH"}
                        """).andExpect(status().isBadRequest());

        var march = Map.of("from", "2001-03-01", "to", "2001-03-31");
        read("/expenses", march)
                .andExpect(jsonPath("$.totalRupees").value(15200))
                .andExpect(jsonPath("$.items[0].description").value("Thread"))
                .andExpect(jsonPath("$.byCategory[0].category").value("RENT"));
        read("/ledger", Map.of("from", "2001-03-01", "to", "2001-03-31", "kind", "EXPENSE"))
                .andExpect(jsonPath("$.openingRupees").value(0))
                .andExpect(jsonPath("$.outRupees").value(15200))
                .andExpect(jsonPath("$.closingRupees").value(-15200))
                .andExpect(jsonPath("$.entries[0].reference").value("Shop rent"))
                .andExpect(jsonPath("$.entries[0].party").value("Landlord"))
                .andExpect(jsonPath("$.byMode[?(@.mode == 'BANK')].outRupees").value(15000.0));

        send(put("/api/v1/admin/money/expenses/" + rent), """
                        {"spentOn":"2001-03-05","category":"RENT","description":"Shop rent (March)",
                         "amountRupees":14000,"mode":"BANK","paidTo":" "}
                        """)
                .andExpect(jsonPath("$.amountRupees").value(14000))
                .andExpect(jsonPath("$.paidTo").doesNotExist());
        mockMvc.perform(delete("/api/v1/admin/money/expenses/" + rent).with(OWNER))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/money/expenses/" + rent).with(OWNER))
                .andExpect(status().isNotFound());
        read("/expenses", march).andExpect(jsonPath("$.totalRupees").value(200));
    }
}

package com.vaabhi.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Every report runs against real data, and the overview adds up (phase 7). */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class ReportsIT {

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Demo Owner"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    private static final RequestPostProcessor COUNTER = jwt().jwt(j -> j.subject("staff:1"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("PERM_ORDERS"));

    private static final AtomicInteger UNIQUE = new AtomicInteger(4_000);

    @Autowired
    private MockMvc mockMvc;

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(
                request.with(OWNER).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    /** Books and delivers an order (invoiced on delivery), paying part of it; returns [orderId, clientId]. */
    private long[] deliveredOrder(String name, long priceRupees, long paidRupees) throws Exception {
        long client = ((Number) JsonPath.read(
                        body(send(post("/api/v1/admin/clients"), """
                                {"phone":"98764%05d","firstName":"%s","gender":"FEMALE","clientType":"RETAIL"}
                                """.formatted(UNIQUE.incrementAndGet(), name))
                                .andExpect(status().isCreated())),
                        "$.id"))
                .longValue();
        var today = LocalDate.now();
        long order = ((Number) JsonPath.read(
                        body(send(post("/api/v1/admin/orders"), """
                                {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                                  {"description":"Report blouse","quantity":1,"priceRupees":%d}],
                                 "discountKind":"AMOUNT","redeemPoints":0,"discountValue":0,"advanceRupees":%d,"advanceMode":"UPI"}
                                """.formatted(
                                                client, today, today, priceRupees, paidRupees))
                                .andExpect(status().isCreated())),
                        "$.id"))
                .longValue();
        send(post("/api/v1/admin/orders/" + order + "/deliver"), "").andExpect(status().isOk());
        return new long[] {order, client};
    }

    private String report(String name) throws Exception {
        var today = LocalDate.now().toString();
        return body(mockMvc.perform(get("/api/v1/admin/reports/" + name)
                        .param("from", today)
                        .param("to", today)
                        .with(OWNER))
                .andExpect(status().isOk()));
    }

    @Test
    void everyReportRuns() throws Exception {
        deliveredOrder("Every", 500, 100);
        List<String> names = JsonPath.read(
                body(mockMvc.perform(get("/api/v1/admin/reports").with(OWNER)).andExpect(status().isOk())),
                "$[*].name");
        assertThat(names).hasSize(16);
        for (var name : names) {
            var report = report(name);
            assertThat(JsonPath.<String>read(report, "$.name")).isEqualTo(name);
            assertThat(JsonPath.<List<Object>>read(report, "$.columns")).isNotEmpty();
        }
        mockMvc.perform(get("/api/v1/admin/reports/nope").with(OWNER)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/reports/sales")
                        .param("from", "2020-01-01")
                        .param("to", "2026-12-31")
                        .with(OWNER))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/reports/sales").with(COUNTER)).andExpect(status().isForbidden());
    }

    @Test
    void salesOrdersDeliveriesAndGstShowTheOrder() throws Exception {
        var ids = deliveredOrder("Rekha", 800, 300);
        String orderNumber = JsonPath.read(
                body(mockMvc.perform(get("/api/v1/admin/orders/" + ids[0]).with(OWNER))), "$.number");

        List<Map<String, Object>> orders =
                JsonPath.read(report("sales-orders"), "$.rows[?(@.number == '" + orderNumber + "')]");
        assertThat(orders).singleElement().satisfies(r -> {
            assertThat(((Number) r.get("total")).longValue()).isEqualTo(800);
            assertThat(((Number) r.get("balance")).longValue()).isEqualTo(500);
            assertThat(r.get("items")).isEqualTo("Report blouse");
        });
        List<Map<String, Object>> delivered =
                JsonPath.read(report("deliveries"), "$.rows[?(@.number == '" + orderNumber + "')]");
        assertThat(delivered)
                .singleElement()
                .satisfies(r ->
                        assertThat(((Number) r.get("days_late")).intValue()).isZero());
        List<Map<String, Object>> gst = JsonPath.read(report("gst"), "$.rows[?(@.customer == 'Rekha')]");
        assertThat(gst)
                .singleElement()
                .satisfies(
                        r -> assertThat(((Number) r.get("total")).longValue()).isEqualTo(800));

        var sales = report("sales");
        long invoiced = ((Number) JsonPath.read(sales, "$.totals.total")).longValue();
        assertThat(invoiced).isGreaterThanOrEqualTo(800);
    }

    @Test
    void overviewAddsUpTheMonth() throws Exception {
        var before = body(mockMvc.perform(get("/api/v1/admin/reports/overview").with(OWNER))
                .andExpect(status().isOk()));
        deliveredOrder("Overview", 600, 200);
        var after = body(mockMvc.perform(get("/api/v1/admin/reports/overview").with(OWNER)));

        assertThat(delta(before, after, "$.bookedRupees")).isEqualTo(600);
        assertThat(delta(before, after, "$.collectedRupees")).isEqualTo(200);
        assertThat(delta(before, after, "$.ordersBooked")).isEqualTo(1);
        assertThat(delta(before, after, "$.ordersDelivered")).isEqualTo(1);
        assertThat(delta(before, after, "$.outstandingRupees")).isEqualTo(400);
        assertThat(JsonPath.<List<Object>>read(after, "$.months")).hasSize(6);
        assertThat(delta(before, after, "$.months[5].bookedRupees")).isEqualTo(600);
        assertThat(JsonPath.<List<Object>>read(after, "$.ageing")).hasSize(5);
        assertThat(delta(before, after, "$.ageing[0].rupees")).isEqualTo(400);
    }

    private static long delta(String before, String after, String path) {
        return ((Number) JsonPath.read(after, path)).longValue() - ((Number) JsonPath.read(before, path)).longValue();
    }
}

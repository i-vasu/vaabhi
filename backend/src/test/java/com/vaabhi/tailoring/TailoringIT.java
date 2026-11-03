package com.vaabhi.tailoring;

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
import com.vaabhi.shared.storage.ObjectStorage;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Customers, orders, payments and alterations against real Postgres, with R2 replaced by an in-memory store. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, TailoringIT.FakeStorage.class})
@RecordApplicationEvents
class TailoringIT {

    /** Stands in for R2: presigned URLs are fake, "uploads" are recorded in {@link #objects}. */
    static class MemoryStorage implements ObjectStorage {
        final Set<String> objects = ConcurrentHashMap.newKeySet();

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public URI presignPut(String key, Duration validFor) {
            return URI.create("https://r2.test/" + key);
        }

        @Override
        public boolean exists(String key) {
            return objects.contains(key);
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeStorage {
        @Bean
        @Primary
        MemoryStorage memoryStorage() {
            return new MemoryStorage();
        }
    }

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Asha Rao"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    /** Every test makes its own customers; the database is shared by the whole test run. */
    private static final AtomicInteger PHONES = new AtomicInteger(1_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MemoryStorage storage;

    @Autowired
    private ApplicationEvents events;

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(
                request.with(OWNER).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String nextPhone() {
        return "98765" + String.format("%05d", PHONES.incrementAndGet());
    }

    private long createClient(String firstName) throws Exception {
        var result = send(post("/api/v1/admin/clients"), """
                        {"phone":"%s","firstName":"%s","gender":"FEMALE","clientType":"RETAIL"}
                        """.formatted(nextPhone(), firstName))
                .andExpect(status().isCreated());
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private long templateId(String name) {
        return jdbc.sql("SELECT id FROM garment_template WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private long stageId(String kind) {
        return jdbc.sql("SELECT id FROM production_stage WHERE kind = :kind AND active ORDER BY position LIMIT 1")
                .param("kind", kind)
                .query(Long.class)
                .single();
    }

    private ResultActions bookBlouse(long clientId, long priceEach, int quantity, String extra) throws Exception {
        var today = LocalDate.now();
        return send(post("/api/v1/admin/orders"), """
                {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                  {"templateId":%d,"description":"3 dart blouse","quantity":%d,"priceRupees":%d,
                   "measurements":{"bust":"34","waist":"28","not_a_field":"9"}}],
                 "discountKind":"AMOUNT","discountValue":0,"advanceRupees":0,"advanceMode":"CASH"%s}
                """.formatted(
                        clientId, today, today.plusDays(7), templateId("Blouse"), quantity, priceEach, extra));
    }

    // --- customers ----------------------------------------------------------------------------------------------

    @Test
    void customersNeedAnAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/alterations")).andExpect(status().isUnauthorized());
    }

    @Test
    void addsFindsAndEditsACustomer() throws Exception {
        var phone = nextPhone();
        var created = send(post("/api/v1/admin/clients"), """
                        {"phone":"%s","firstName":"Meera","lastName":"Iyer","gender":"FEMALE","clientType":"VIP",
                         "city":"Pune","pinCode":"411001","occasionName":"Wedding","occasionDate":"2027-01-20"}
                        """.formatted(phone))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+91" + phone))
                .andExpect(jsonPath("$.orderCount").value(0));
        long id = ((Number) JsonPath.read(body(created), "$.id")).longValue();

        mockMvc.perform(get("/api/v1/admin/clients")
                        .param("q", phone.substring(4))
                        .with(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].name").value("Meera Iyer"));
        mockMvc.perform(get("/api/v1/admin/clients").param("q", "meera iy").with(OWNER))
                .andExpect(jsonPath("$.items[?(@.id == " + id + ")]").exists());

        send(put("/api/v1/admin/clients/" + id), """
                        {"phone":"%s","firstName":"Meera","gender":"FEMALE","clientType":"RETAIL","city":"Mumbai"}
                        """.formatted(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Mumbai"))
                .andExpect(jsonPath("$.lastName").doesNotExist());
    }

    @Test
    void oneCustomerPerMobileNumber() throws Exception {
        var phone = nextPhone();
        var form = """
                {"phone":"%s","firstName":"Ravi","gender":"MALE","clientType":"RETAIL"}
                """.formatted(phone);
        send(post("/api/v1/admin/clients"), form).andExpect(status().isCreated());
        send(post("/api/v1/admin/clients"), form.replace(phone, "0" + phone)).andExpect(status().isConflict());
        send(post("/api/v1/admin/clients"), form.replace(phone, "12345")).andExpect(status().isBadRequest());
    }

    @Test
    void keepsMeasurementsPerGarment() throws Exception {
        long id = createClient("Lata");
        send(post("/api/v1/admin/clients/" + id + "/measurements"), """
                        {"templateId":%d,"values":{"bust":"34","waist":" 28 ","shoulder":""},"notes":"loose fit"}
                        """.formatted(templateId("Blouse")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.garment").value("Blouse"))
                .andExpect(jsonPath("$.values.waist").value("28"))
                .andExpect(jsonPath("$.values.shoulder").doesNotExist())
                .andExpect(jsonPath("$.takenBy").value("Asha Rao"));
        send(post("/api/v1/admin/clients/" + id + "/measurements"), """
                        {"templateId":%d,"values":{"inseam":"30"}}
                        """.formatted(templateId("Blouse")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/clients/" + id).with(OWNER))
                .andExpect(jsonPath("$.measurements.length()").value(1));
    }

    // --- orders -------------------------------------------------------------------------------------------------

    @Test
    void booksAnOrderWithDiscountAndAdvance() throws Exception {
        long client = createClient("Kavya");
        var today = LocalDate.now();
        var result = send(post("/api/v1/admin/orders"), """
                        {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                          {"templateId":%d,"description":"3 dart blouse","quantity":2,"priceRupees":50000,
                           "measurements":{"bust":"34","not_a_field":"9"}},
                          {"description":"Fall & pico","quantity":1,"priceRupees":10000}],
                         "discountKind":"PERCENT","discountValue":1000,"couponCode":" ","advanceRupees":30000,
                         "advanceMode":"UPI","notes":"Wedding on the 20th"}
                        """.formatted(
                                client, today, today.plusDays(10), templateId("Blouse")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.itemsRupees").value(110000))
                .andExpect(jsonPath("$.discountRupees").value(11000))
                .andExpect(jsonPath("$.totalRupees").value(99000))
                .andExpect(jsonPath("$.paidRupees").value(30000))
                .andExpect(jsonPath("$.balanceRupees").value(69000))
                .andExpect(jsonPath("$.couponCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].measurements.bust").value("34"))
                .andExpect(jsonPath("$.items[0].measurements.not_a_field").doesNotExist())
                .andExpect(jsonPath("$.items[0].stage.kind").value("WORK"))
                .andExpect(jsonPath("$.payments[0].kind").value("ADVANCE"))
                .andExpect(jsonPath("$.payments[0].mode").value("UPI"))
                .andExpect(jsonPath("$.createdBy").value("Asha Rao"));
        long orderId = ((Number) JsonPath.read(body(result), "$.id")).longValue();
        String number = JsonPath.read(body(result), "$.number");

        mockMvc.perform(get("/api/v1/admin/orders").param("q", number).with(OWNER))
                .andExpect(jsonPath("$.items[0].id").value(orderId))
                .andExpect(jsonPath("$.items[0].itemCount").value(3))
                .andExpect(jsonPath("$.items[0].itemsLabel").value("3 dart blouse, Fall & pico"));
        mockMvc.perform(get("/api/v1/admin/clients/" + client).with(OWNER))
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.outstandingRupees").value(69000));
        assertThat(events.stream(OrderEvent.class))
                .anyMatch(e -> e.type() == OrderEvent.Type.PLACED && e.orderId() == orderId);
    }

    @Test
    void rejectsImpossibleBookings() throws Exception {
        long client = createClient("Nisha");
        bookBlouse(client, 50000, 1, ",\"advanceRupees\":60000").andExpect(status().isBadRequest());
        bookBlouse(client, 50000, 1, ",\"discountKind\":\"PERCENT\",\"discountValue\":20000")
                .andExpect(status().isBadRequest());
        var today = LocalDate.now();
        send(post("/api/v1/admin/orders"), """
                        {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                          {"description":"Blouse","quantity":1,"priceRupees":100}],
                         "discountKind":"AMOUNT","discountValue":0,"advanceRupees":0,"advanceMode":"CASH"}
                        """.formatted(client, today, today.minusDays(1)))
                .andExpect(status().isBadRequest());
        send(post("/api/v1/admin/orders"), """
                        {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[],
                         "discountKind":"AMOUNT","discountValue":0,"advanceRupees":0,"advanceMode":"CASH"}
                        """.formatted(client, today, today)).andExpect(status().isBadRequest());
    }

    @Test
    void itemsMoveThroughStagesAndTheOrderFollows() throws Exception {
        long client = createClient("Priya");
        var booked = bookBlouse(client, 40000, 1, "").andExpect(status().isCreated());
        long orderId = ((Number) JsonPath.read(body(booked), "$.id")).longValue();

        long secondWork = jdbc.sql("""
                        SELECT id FROM production_stage WHERE kind = 'WORK' AND active ORDER BY position OFFSET 1 LIMIT 1
                        """).query(Long.class).single();
        send(post("/api/v1/admin/orders/" + orderId + "/stage"), """
                        {"stageId":%d,"itemIds":[]}
                        """.formatted(secondWork))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        send(post("/api/v1/admin/orders/" + orderId + "/stage"), """
                        {"stageId":%d,"itemIds":[]}
                        """.formatted(stageId("READY")))
                .andExpect(jsonPath("$.status").value("READY"));
        send(post("/api/v1/admin/orders/" + orderId + "/deliver"), "")
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").exists());
        send(post("/api/v1/admin/orders/" + orderId + "/cancel"), "").andExpect(status().isBadRequest());

        assertThat(events.stream(OrderEvent.class).filter(e -> e.orderId() == orderId))
                .extracting(OrderEvent::type)
                .containsSubsequence(OrderEvent.Type.PLACED, OrderEvent.Type.READY, OrderEvent.Type.DELIVERED);
    }

    @Test
    void takesPaymentsAndRefunds() throws Exception {
        long client = createClient("Anu");
        var booked = bookBlouse(client, 50000, 1, "").andExpect(status().isCreated());
        long orderId = ((Number) JsonPath.read(body(booked), "$.id")).longValue();

        send(post("/api/v1/admin/orders/" + orderId + "/payments"), """
                        {"amountRupees":20000,"mode":"CASH","refund":false}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceRupees").value(30000));
        send(post("/api/v1/admin/orders/" + orderId + "/payments"), """
                        {"amountRupees":30000,"mode":"CASH","refund":true}
                        """).andExpect(status().isBadRequest());
        send(post("/api/v1/admin/orders/" + orderId + "/cancel"), "")
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        send(post("/api/v1/admin/orders/" + orderId + "/payments"), """
                        {"amountRupees":100,"mode":"UPI","refund":false}
                        """).andExpect(status().isBadRequest());
        send(post("/api/v1/admin/orders/" + orderId + "/payments"), """
                        {"amountRupees":20000,"mode":"UPI","refund":true,"note":"Cancelled"}
                        """)
                .andExpect(jsonPath("$.paidRupees").value(0))
                .andExpect(jsonPath("$.payments[1].kind").value("REFUND"))
                .andExpect(jsonPath("$.payments[1].amountRupees").value(-20000));
    }

    @Test
    void reschedulesAndListsOverdueOrders() throws Exception {
        long client = createClient("Deepa");
        var today = LocalDate.now();
        var booked = send(post("/api/v1/admin/orders"), """
                        {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                          {"description":"Kurti","quantity":1,"priceRupees":70000}],
                         "discountKind":"AMOUNT","discountValue":0,"advanceRupees":0,"advanceMode":"CASH"}
                        """.formatted(client, today.minusDays(10), today.minusDays(2)))
                .andExpect(status().isCreated());
        long orderId = ((Number) JsonPath.read(body(booked), "$.id")).longValue();

        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("filter", "OVERDUE")
                        .param("clientId", Long.toString(client))
                        .with(OWNER))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].overdue").value(true));

        send(put("/api/v1/admin/orders/" + orderId + "/schedule"), """
                        {"deliveryDate":"%s","dueTime":"17:30","notes":"Customer asked for more time"}
                        """.formatted(today.plusDays(3)))
                .andExpect(jsonPath("$.deliveryDate").value(today.plusDays(3).toString()))
                .andExpect(jsonPath("$.dueTime").value("17:30:00"));
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("filter", "OVERDUE")
                        .param("clientId", Long.toString(client))
                        .with(OWNER))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void clothPhotosMustBeUploadedFirst() throws Exception {
        long client = createClient("Sunita");
        var upload = send(post("/api/v1/admin/tailoring/images"), "{\"purpose\":\"CLOTH\"}")
                .andExpect(status().isOk());
        String key = JsonPath.read(body(upload), "$.key");
        assertThat(key).startsWith("tailoring/cloth/");
        var withPhoto = ",\"items\":[{\"description\":\"Blouse\",\"quantity\":1,\"priceRupees\":100,"
                + "\"clothPhotoKey\":\"" + key + "\"}]";

        bookBlouse(client, 100, 1, withPhoto).andExpect(status().isBadRequest());
        storage.objects.add(key + "/400.webp");
        bookBlouse(client, 100, 1, withPhoto)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].clothPhotoKey").value(key));
    }

    // --- price list ---------------------------------------------------------------------------------------------

    @Test
    void priceListShowsProfitAndHidesRemovedProducts() throws Exception {
        var created = send(post("/api/v1/admin/service-products"), """
                        {"templateId":%d,"name":"Padded blouse","priceRupees":90000,
                         "stageCosts":[{"stageId":%d,"rupees":20000},{"stageId":%d,"rupees":0}]}
                        """.formatted(
                                templateId("Blouse"), stageId("WORK"), stageId("WORK")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.profitRupees").value(70000))
                .andExpect(jsonPath("$.stageCosts.length()").value(1));
        long id = ((Number) JsonPath.read(body(created), "$.id")).longValue();

        send(post("/api/v1/admin/service-products"), """
                        {"templateId":%d,"name":"Bad","priceRupees":100,"stageCosts":[{"stageId":%d,"rupees":10}]}
                        """.formatted(templateId("Blouse"), stageId("READY")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/service-products")
                        .param("q", "padded")
                        .with(OWNER))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
        mockMvc.perform(delete("/api/v1/admin/service-products/" + id).with(OWNER))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/service-products")
                        .param("q", "padded")
                        .with(OWNER))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
    }

    // --- alterations --------------------------------------------------------------------------------------------

    @Test
    void alterationGoesFromBookedToDeliveredAndCollects() throws Exception {
        long client = createClient("Radha");
        var created = send(post("/api/v1/admin/alterations"), """
                        {"clientId":%d,"readyBy":"%s","paid":true,"undertakingAccepted":true,"items":[
                          {"kind":"LENGTH","garment":"Silk saree blouse","instructions":"Shorten 1 inch",
                           "costRupees":15000},
                          {"kind":"REPAIR","garment":"Lehenga","costRupees":25000}]}
                        """.formatted(
                                client, LocalDate.now().plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.totalRupees").value(40000))
                .andExpect(jsonPath("$.balanceRupees").value(40000))
                .andExpect(jsonPath("$.undertaking").isNotEmpty())
                .andExpect(jsonPath("$.items.length()").value(2));
        long id = ((Number) JsonPath.read(body(created), "$.id")).longValue();

        send(post("/api/v1/admin/alterations/" + id + "/progress"), "{\"status\":\"READY\",\"collectRupees\":0}")
                .andExpect(jsonPath("$.status").value("READY"));
        send(post("/api/v1/admin/alterations/" + id + "/progress"), "{\"status\":\"BOOKED\",\"collectRupees\":0}")
                .andExpect(status().isBadRequest());
        send(
                        post("/api/v1/admin/alterations/" + id + "/progress"),
                        "{\"status\":\"DELIVERED\",\"collectRupees\":50000}")
                .andExpect(status().isBadRequest());
        send(
                        post("/api/v1/admin/alterations/" + id + "/progress"),
                        "{\"status\":\"DELIVERED\",\"collectRupees\":40000}")
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.balanceRupees").value(0))
                .andExpect(jsonPath("$.deliveredAt").exists());
        send(post("/api/v1/admin/alterations/" + id + "/progress"), "{\"status\":\"CANCELLED\",\"collectRupees\":0}")
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/alterations")
                        .param("filter", "DELIVERED")
                        .param("clientId", Long.toString(client))
                        .with(OWNER))
                .andExpect(jsonPath("$.items[0].itemsLabel").value("Silk saree blouse, Lehenga"));
        assertThat(events.stream(AlterationEvent.class).filter(e -> e.alterationId() == id))
                .extracting(AlterationEvent::type)
                .containsExactly(
                        AlterationEvent.Type.BOOKED,
                        AlterationEvent.Type.READY,
                        AlterationEvent.Type.PAYMENT_RECEIVED,
                        AlterationEvent.Type.DELIVERED);
    }

    @Test
    void freeAlterationsCostNothingAndNeedTheUndertaking() throws Exception {
        long client = createClient("Geeta");
        long other = createClient("Hema");
        var order = bookBlouse(client, 50000, 1, "").andExpect(status().isCreated());
        long orderId = ((Number) JsonPath.read(body(order), "$.id")).longValue();
        var form = """
                {"clientId":%d,"orderId":%d,"readyBy":"%s","paid":false,"undertakingAccepted":%s,"items":[
                  {"kind":"FIT_ADJUSTMENT","garment":"3 dart blouse","costRupees":5000}]}
                """;
        var readyBy = LocalDate.now().plusDays(1);

        send(post("/api/v1/admin/alterations"), form.formatted(client, orderId, readyBy, false))
                .andExpect(status().isBadRequest());
        send(post("/api/v1/admin/alterations"), form.formatted(other, orderId, readyBy, true))
                .andExpect(status().isBadRequest());
        send(post("/api/v1/admin/alterations"), form.formatted(client, orderId, readyBy, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRupees").value(0))
                .andExpect(jsonPath("$.items[0].costRupees").value(0))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.orderNumber").isNotEmpty());
    }
}

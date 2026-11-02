package com.vaabhi.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

/** The work board, assigning, stage permissions and the workshop overview against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class WorkshopIT {

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Asha Rao"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    /** Every test makes its own customers and staff; the database is shared by the whole test run. */
    private static final AtomicInteger PHONES = new AtomicInteger(5_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(
                request.with(OWNER).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(body(result), path)).longValue();
    }

    private long staffMember(String name) {
        return jdbc.sql("""
                        INSERT INTO staff_account (name, email, role, password_hash)
                        VALUES (:name, :email, 'STAFF', 'not-a-real-hash') RETURNING id
                        """)
                .param("name", name)
                .param("email", UUID.randomUUID() + "@vaabhi.test")
                .query(Long.class)
                .single();
    }

    private long stage(String kind, int offset) {
        return jdbc.sql(
                        "SELECT id FROM production_stage WHERE kind = :kind AND active ORDER BY position OFFSET :n LIMIT 1")
                .param("kind", kind)
                .param("n", offset)
                .query(Long.class)
                .single();
    }

    /** Books an order of one blouse for a new customer, due {@code dueInDays} from today; returns [orderId, itemId]. */
    private long[] book(String name, int dueInDays, int quantity) throws Exception {
        var client = send(post("/api/v1/admin/clients"), """
                        {"phone":"98760%05d","firstName":"%s","gender":"FEMALE","clientType":"RETAIL"}
                        """.formatted(PHONES.incrementAndGet(), name))
                .andExpect(status().isCreated());
        var today = LocalDate.now();
        var order = send(post("/api/v1/admin/orders"), """
                        {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                          {"description":"Blouse for %s","quantity":%d,"pricePaise":50000}],
                         "discountKind":"AMOUNT","discountValue":0,"advancePaise":0,"advanceMode":"CASH"}
                        """.formatted(
                                id(client, "$.id"),
                                dueInDays < 0 ? today.plusDays(dueInDays) : today,
                                today.plusDays(dueInDays),
                                name,
                                quantity))
                .andExpect(status().isCreated());
        return new long[] {id(order, "$.id"), id(order, "$.items[0].id")};
    }

    private ResultActions board(String query) throws Exception {
        return mockMvc.perform(
                get("/api/v1/admin/workshop/board").param("q", query).with(OWNER));
    }

    @Test
    void workshopNeedsAnAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/workshop/board")).andExpect(status().isUnauthorized());
    }

    @Test
    void boardShowsOpenItemsByStageMostUrgentFirst() throws Exception {
        book("Charu", 5, 1);
        book("Charu", -1, 1);

        board("charu")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stage.kind").value("WORK"))
                .andExpect(jsonPath("$[0].items.length()").value(2))
                .andExpect(jsonPath("$[0].items[0].overdue").value(true))
                .andExpect(jsonPath("$[0].items[1].overdue").value(false))
                .andExpect(jsonPath("$[0].items[0].clientName").value("Charu"))
                .andExpect(jsonPath("$[?(@.stage.kind == 'READY')]").exists())
                .andExpect(jsonPath("$[?(@.stage.kind == 'DELIVERED')]").doesNotExist());
    }

    @Test
    void assignsRespectingStagesAndClearsOnTheNextStage() throws Exception {
        long tailor = staffMember("Ravi Tailor");
        long cutter = staffMember("Sita Cutter");
        var booked = book("Divya", 4, 1);
        long orderId = booked[0];
        long itemId = booked[1];

        send(
                        put("/api/v1/admin/workshop/staff/" + cutter + "/stages"),
                        "{\"stageIds\":[%d]}".formatted(stage("WORK", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageIds[0]").value(stage("WORK", 0)));
        send(
                        put("/api/v1/admin/workshop/staff/" + cutter + "/stages"),
                        "{\"stageIds\":[%d]}".formatted(stage("READY", 0)))
                .andExpect(status().isBadRequest());

        send(put("/api/v1/admin/workshop/items/" + itemId + "/assignee"), "{\"staffId\":" + cutter + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.name").value("Sita Cutter"));
        mockMvc.perform(get("/api/v1/admin/orders/" + orderId).with(OWNER))
                .andExpect(jsonPath("$.items[0].assignedTo.id").value(cutter));
        mockMvc.perform(get("/api/v1/admin/workshop/board")
                        .param("staffId", Long.toString(cutter))
                        .with(OWNER))
                .andExpect(jsonPath("$[0].items[?(@.itemId == " + itemId + ")]").exists());

        send(
                        post("/api/v1/admin/orders/" + orderId + "/stage"),
                        "{\"stageId\":%d,\"itemIds\":[]}".formatted(stage("WORK", 1)))
                .andExpect(jsonPath("$.items[0].assignedTo").doesNotExist());
        var log = jdbc.sql("SELECT from_stage_id, to_stage_id, staff_id FROM order_item_stage_log WHERE item_id = :id")
                .param("id", itemId)
                .query((rs, n) -> List.of(rs.getLong(1), rs.getLong(2), rs.getLong(3)))
                .single();
        assertThat(log).containsExactly(stage("WORK", 0), stage("WORK", 1), cutter);

        // The cutter only cuts; the tailor (no stages set) may take any work stage.
        send(put("/api/v1/admin/workshop/items/" + itemId + "/assignee"), "{\"staffId\":" + cutter + "}")
                .andExpect(status().isBadRequest());
        send(put("/api/v1/admin/workshop/items/" + itemId + "/assignee"), "{\"staffId\":" + tailor + "}")
                .andExpect(jsonPath("$.assignedTo.id").value(tailor));
        send(put("/api/v1/admin/workshop/items/" + itemId + "/assignee"), "{\"staffId\":null}")
                .andExpect(jsonPath("$.assignedTo").doesNotExist());
    }

    @Test
    void readyItemsCannotBeAssigned() throws Exception {
        long tailor = staffMember("Mohan");
        var booked = book("Esha", 3, 1);
        send(
                        post("/api/v1/admin/orders/" + booked[0] + "/stage"),
                        "{\"stageId\":%d,\"itemIds\":[]}".formatted(stage("READY", 0)))
                .andExpect(status().isOk());
        send(put("/api/v1/admin/workshop/items/" + booked[1] + "/assignee"), "{\"staffId\":" + tailor + "}")
                .andExpect(status().isBadRequest());
        send(post("/api/v1/admin/orders/" + booked[0] + "/deliver"), "").andExpect(status().isOk());
        send(put("/api/v1/admin/workshop/items/" + booked[1] + "/assignee"), "{\"staffId\":" + tailor + "}")
                .andExpect(status().isNotFound());
    }

    @Test
    void movesItemsOfSeveralOrdersAtOnce() throws Exception {
        var first = book("Farah", 2, 1);
        var second = book("Farah", 6, 1);
        send(
                        post("/api/v1/admin/workshop/items/move"),
                        "{\"stageId\":%d,\"itemIds\":[%d,%d]}".formatted(stage("READY", 0), first[1], second[1]))
                .andExpect(status().isOk());
        for (var order : List.of(first[0], second[0])) {
            mockMvc.perform(get("/api/v1/admin/orders/" + order).with(OWNER))
                    .andExpect(jsonPath("$.status").value("READY"));
        }
        send(
                        post("/api/v1/admin/workshop/items/move"),
                        "{\"stageId\":%d,\"itemIds\":[999999999]}".formatted(stage("READY", 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overviewCountsLoadAndDeadlines() throws Exception {
        var before = body(mockMvc.perform(get("/api/v1/admin/workshop/overview").with(OWNER)));
        long tailor = staffMember("Gopal");
        book("Gita", 0, 3);
        var assigned = book("Gita", -2, 2);
        send(put("/api/v1/admin/workshop/items/" + assigned[1] + "/assignee"), "{\"staffId\":" + tailor + "}")
                .andExpect(status().isOk());

        var after = body(mockMvc.perform(get("/api/v1/admin/workshop/overview").with(OWNER))
                .andExpect(jsonPath("$.staffLoad[?(@.id == " + tailor + ")].workingOn")
                        .value(2)));
        assertThat(delta(before, after, "$.inProduction")).isEqualTo(5);
        assertThat(delta(before, after, "$.unassigned")).isEqualTo(3);
        assertThat(delta(before, after, "$.dueToday")).isEqualTo(1);
        assertThat(delta(before, after, "$.overdue")).isEqualTo(1);
        assertThat(delta(before, after, "$.stageLoad[0].items")).isEqualTo(5);
        assertThat(delta(before, after, "$.stageLoad[0].overdue")).isEqualTo(2);

        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("filter", "DUE_TODAY")
                        .param("q", "gita")
                        .with(OWNER))
                .andExpect(jsonPath("$.total").value(1));
    }

    private static int delta(String before, String after, String path) {
        return JsonPath.<Integer>read(after, path) - JsonPath.<Integer>read(before, path);
    }
}

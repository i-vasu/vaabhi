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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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

/** "Make products": stock cut into pieces that become draft products and move through the workshop board. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class PiecesIT {

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Asha Rao"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

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

    private String category() {
        return jdbc.sql("SELECT slug FROM category ORDER BY position LIMIT 1")
                .query(String.class)
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

    /** A new stock item with {@code onHand} in stock; returns its id. */
    private long stock(String unit, int onHand) throws Exception {
        var item = send(post("/api/v1/admin/stock"), """
                        {"name":"Kalamkari %s","unit":"%s","reorderLevel":0,"costRupees":200,"gsm":120,
                         "threadCount":60,"material":"Cotton","colour":"Maroon","widthIn":44}
                        """.formatted(UUID.randomUUID(), unit))
                .andExpect(status().isCreated());
        long itemId = id(item, "$.id");
        send(post("/api/v1/admin/stock/" + itemId + "/movements"), """
                        {"kind":"IN","quantity":%d}
                        """.formatted(onHand))
                .andExpect(status().isCreated());
        return itemId;
    }

    private ResultActions make(long itemId, String unitName, double... quantities) throws Exception {
        var pieces = new StringBuilder();
        for (int i = 0; i < quantities.length; i++) {
            if (i > 0) {
                pieces.append(',');
            }
            pieces.append("{\"name\":\"%s %d\",\"categorySlug\":\"%s\",\"quantity\":%s}"
                    .formatted(unitName, i, category(), quantities[i]));
        }
        return send(post("/api/v1/admin/workshop/pieces"), """
                        {"stockItemId":%d,"pieces":[%s]}
                        """.formatted(itemId, pieces));
    }

    private BigDecimal onHand(long itemId) throws Exception {
        var item = body(mockMvc.perform(get("/api/v1/admin/stock/" + itemId).with(OWNER)));
        return new BigDecimal(JsonPath.read(item, "$.onHand").toString());
    }

    private int productCount() {
        return jdbc.sql("SELECT COUNT(*) FROM product").query(Integer.class).single();
    }

    @Test
    void makesDraftProductsAndTakesFabric() throws Exception {
        long item = stock("M", 10);
        var made = make(item, "Peacock " + UUID.randomUUID(), 3, 3, 3).andExpect(status().isCreated());
        List<Integer> productIds = JsonPath.read(body(made), "$[*].productId");
        assertThat(productIds).hasSize(3);
        assertThat(onHand(item)).isEqualByComparingTo("1");

        for (var productId : productIds) {
            mockMvc.perform(get("/api/v1/admin/products/" + productId).with(OWNER))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.gsm").value(120))
                    .andExpect(jsonPath("$.threadCount").value(60));
            mockMvc.perform(get("/api/v1/admin/workshop/pieces/by-product/" + productId)
                            .with(OWNER))
                    .andExpect(jsonPath("$.stage.id").value(stage("WORK", 0)))
                    .andExpect(jsonPath("$.quantity").value(3.0))
                    .andExpect(jsonPath("$.fabric.material").value("Cotton"));
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM product_material WHERE stock_item_id = :item")
                        .param("item", item)
                        .query(Integer.class)
                        .single())
                .isEqualTo(3);
    }

    @Test
    void refusesMoreThanOnHand() throws Exception {
        long item = stock("M", 10);
        int before = productCount();
        make(item, "Too much", 3, 3, 3, 3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only 10 M left"));
        assertThat(productCount()).isEqualTo(before);
    }

    @Test
    void refusesInPieces() throws Exception {
        long item = stock("PCS", 2);
        make(item, "Dupatta", 1, 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only 2 PCS left"));
    }

    @Test
    void boardShowsPiecesByStageAndFilters() throws Exception {
        long item = stock("M", 5);
        var name = "Lotus " + UUID.randomUUID();
        make(item, name, 2).andExpect(status().isCreated());
        var stockName = jdbc.sql("SELECT name FROM stock_item WHERE id = :id")
                .param("id", item)
                .query(String.class)
                .single();

        mockMvc.perform(get("/api/v1/admin/workshop/board")
                        .param("q", stockName)
                        .with(OWNER))
                .andExpect(jsonPath("$[0].pieces[0].productName").value(name + " 0"));
        mockMvc.perform(get("/api/v1/admin/workshop/board")
                        .param("q", stockName)
                        .param("staffId", "999999")
                        .with(OWNER))
                .andExpect(jsonPath("$[0].pieces").isEmpty());
    }

    @Test
    void publishedPieceLeavesBoardAndCannotMove() throws Exception {
        long item = stock("M", 5);
        var made = make(item, "Published " + UUID.randomUUID(), 2).andExpect(status().isCreated());
        long productId = id(made, "$[0].productId");
        long pieceId = id(made, "$[0].pieceId");
        jdbc.sql("UPDATE product SET status = 'PUBLISHED', published_at = now() WHERE id = :id")
                .param("id", productId)
                .update();

        mockMvc.perform(get("/api/v1/admin/workshop/pieces/by-product/" + productId)
                        .with(OWNER))
                .andExpect(status().isNotFound());
        send(post("/api/v1/admin/workshop/items/move"), """
                        {"stageId":%d,"pieceIds":[%d]}
                        """.formatted(stage("WORK", 1), pieceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void movingWithWorkLogsCost() throws Exception {
        long item = stock("M", 5);
        var made = make(item, "Embroidered " + UUID.randomUUID(), 2).andExpect(status().isCreated());
        long productId = id(made, "$[0].productId");
        long pieceId = id(made, "$[0].pieceId");
        var firstStage = jdbc.sql("SELECT name FROM production_stage WHERE id = :id")
                .param("id", stage("WORK", 0))
                .query(String.class)
                .single();

        send(post("/api/v1/admin/workshop/items/move"), """
                        {"stageId":%d,"pieceIds":[%d],"work":{"workerName":"Lakshmi","costRupees":800}}
                        """.formatted(stage("WORK", 1), pieceId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/products/" + productId + "/making").with(OWNER))
                .andExpect(jsonPath("$.work[0].workType").value(firstStage))
                .andExpect(jsonPath("$.work[0].costRupees").value(800.0));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM workshop_piece_stage_log WHERE piece_id = :id")
                        .param("id", pieceId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void bulkMoveRejectsWork() throws Exception {
        long item = stock("M", 5);
        var made = make(item, "Pair " + UUID.randomUUID(), 2, 2).andExpect(status().isCreated());
        send(post("/api/v1/admin/workshop/items/move"), """
                        {"stageId":%d,"pieceIds":[%d,%d],"work":{"workerName":"Lakshmi","costRupees":800}}
                        """.formatted(
                                stage("WORK", 1), id(made, "$[0].pieceId"), id(made, "$[1].pieceId")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotDeliverAPiece() throws Exception {
        long item = stock("M", 5);
        var made = make(item, "Deliver " + UUID.randomUUID(), 2).andExpect(status().isCreated());
        send(post("/api/v1/admin/workshop/items/move"), """
                        {"stageId":%d,"pieceIds":[%d]}
                        """.formatted(stage("DELIVERED", 0), id(made, "$[0].pieceId")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Publish the product instead"));
    }

    @Test
    void assignsAPieceInAWorkStage() throws Exception {
        long item = stock("M", 5);
        var made = make(item, "Assigned " + UUID.randomUUID(), 2).andExpect(status().isCreated());
        long tailor = jdbc.sql("""
                        INSERT INTO staff_account (name, email, role, password_hash, phone)
                        VALUES ('Gopal', :email, 'STAFF', 'not-a-real-hash', '9876543210') RETURNING id
                        """)
                .param("email", UUID.randomUUID() + "@vaabhi.test")
                .query(Long.class)
                .single();
        send(
                        put("/api/v1/admin/workshop/pieces/" + id(made, "$[0].pieceId") + "/assignee"),
                        "{\"staffId\":" + tailor + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.name").value("Gopal"))
                .andExpect(jsonPath("$.assignedPhone").value("9876543210"));
    }
}

package com.vaabhi.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Bag pricing against the demo catalogue: current prices, stock limits and withdrawn items. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class StoreBagIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private String sku(String slug) {
        return jdbc.sql("""
                        SELECT v.sku FROM product_variant v JOIN product p ON p.id = v.product_id
                        WHERE p.slug = :slug ORDER BY v.position LIMIT 1
                        """).param("slug", slug).query(String.class).single();
    }

    private String line(String sku, String addon, int quantity) {
        return "{\"sku\":\"" + sku + "\",\"addonCode\":" + (addon == null ? "null" : "\"" + addon + "\"")
                + ",\"quantity\":" + quantity + "}";
    }

    @Test
    void pricesTheBagFromTheCatalogue() throws Exception {
        var kanjeevaram = sku("kanjeevaram-silk-saree-temple-border");
        var paithani = sku("paithani-silk-saree-peacock-pallu");
        var linen = sku("linen-saree-block-print");
        var draft = sku("tissue-silk-saree-draft");
        var body = "{\"lines\":["
                + String.join(
                        ",",
                        line(kanjeevaram, "blouse-standard", 1),
                        line(paithani, null, 3),
                        line(linen, null, 1),
                        line(draft, null, 1),
                        line("NOPE-1", null, 1),
                        line(kanjeevaram, "blouse-gold-plated", 1))
                + "]}";

        mockMvc.perform(post("/api/v1/store/bag/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.lines[0].name").value("Kanjeevaram silk saree"))
                .andExpect(jsonPath("$.lines[0].pricePaise").value(1850000))
                .andExpect(jsonPath("$.lines[0].addonName").value("Stitched blouse, standard size"))
                .andExpect(jsonPath("$.lines[0].addonPricePaise").value(120000))
                .andExpect(jsonPath("$.lines[0].addonInput").value("SIZE"))
                .andExpect(jsonPath("$.lines[1].status").value("LIMITED"))
                .andExpect(jsonPath("$.lines[1].maxQuantity").value(2))
                .andExpect(jsonPath("$.lines[1].stockLeft").value(2))
                .andExpect(jsonPath("$.lines[2].status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.lines[2].maxQuantity").value(0))
                .andExpect(jsonPath("$.lines[3].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.lines[4].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.lines[5].status").value("UNAVAILABLE"))
                // 18,500 + 2 × 21,500 = 61,500 for items; one stitched blouse 1,200.
                .andExpect(jsonPath("$.itemsPaise").value(6150000))
                .andExpect(jsonPath("$.addonsPaise").value(120000))
                .andExpect(jsonPath("$.totalPaise").value(6270000))
                .andExpect(jsonPath("$.mrpPaise").value(2200000 + 120000 + 2 * 2150000));
    }

    @Test
    void emptyBagCostsNothing() throws Exception {
        mockMvc.perform(post("/api/v1/store/bag/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaise").value(0));
    }
}

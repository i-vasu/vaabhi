package com.vaabhi.catalog;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Listing and product page SQL against real Postgres with the demo catalogue loaded. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class StoreCatalogueIT {

    private static final String SAREES = "/api/v1/store/categories/sarees/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void newArrivalsListEveryCategoryNewestPublishedFirst() throws Exception {
        // Other tests publish products too, so the expected order comes from the database.
        List<String> newest = jdbc.sql("""
                        SELECT slug FROM product p WHERE status = 'PUBLISHED'
                          AND EXISTS (SELECT 1 FROM product_variant v WHERE v.product_id = p.id AND v.active)
                        ORDER BY published_at DESC, id DESC LIMIT 3
                        """)
                .query(String.class)
                .list();
        long published = jdbc.sql("""
                        SELECT COUNT(*) FROM product p WHERE status = 'PUBLISHED'
                          AND EXISTS (SELECT 1 FROM product_variant v WHERE v.product_id = p.id AND v.active)
                        """)
                .query(Long.class)
                .single();

        mockMvc.perform(get("/api/v1/store/new-arrivals").param("pageSize", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(published))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].slug").value(newest.get(0)))
                .andExpect(jsonPath("$.items[1].slug").value(newest.get(1)))
                .andExpect(jsonPath("$.items[2].slug").value(newest.get(2)));
        mockMvc.perform(get("/api/v1/store/new-arrivals").param("pageSize", "48"))
                .andExpect(jsonPath("$.items[*].slug", not(hasItem("tissue-silk-saree-draft"))))
                .andExpect(jsonPath("$.items[*].slug", hasItem("velvet-bridal-lehenga-zardozi")));
        mockMvc.perform(get("/api/v1/store/new-arrivals").param("pageSize", "49"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsOnlyPublishedProducts() throws Exception {
        mockMvc.perform(get(SAREES).param("pageSize", "48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.description").isNotEmpty())
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.items[*].slug", not(hasItem("tissue-silk-saree-draft"))))
                .andExpect(jsonPath("$.items[0].slug").value("kanjeevaram-silk-saree-temple-border"))
                .andExpect(jsonPath("$.items[0].colours.length()").value(2))
                .andExpect(jsonPath("$.priceRange.minPaise").value(160000))
                .andExpect(jsonPath("$.priceRange.maxPaise").value(2490000));
    }

    @Test
    void filterCountsIgnoreTheirOwnSelection() throws Exception {
        mockMvc.perform(get(SAREES).param("fabric", "silk").param("occasion", "wedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.filters[?(@.code == 'occasion')].options[?(@.code == 'wedding')].count")
                        .value(3))
                .andExpect(jsonPath("$.filters[?(@.code == 'occasion')].options[?(@.code == 'festive')].count")
                        .value(1))
                .andExpect(jsonPath("$.filters[?(@.code == 'fabric')].options[?(@.code == 'silk')].selected")
                        .value(true));
    }

    @Test
    void colourSizeAndStockMatchOneVariant() throws Exception {
        mockMvc.perform(get("/api/v1/store/categories/lehengas/products")
                        .param("colour", "red")
                        .param("size", "M")
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("velvet-bridal-lehenga-zardozi"))
                .andExpect(jsonPath("$.filters[?(@.code == 'size')].options[*].code", hasItem("XXL")));
    }

    @Test
    void sortsAndPages() throws Exception {
        mockMvc.perform(get(SAREES)
                        .param("sort", "price_asc")
                        .param("pageSize", "2")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].slug").value("linen-saree-block-print"))
                .andExpect(jsonPath("$.items[0].inStock").value(false))
                .andExpect(jsonPath("$.items[1].slug").value("jamdani-cotton-saree"));
    }

    @Test
    void priceFilterKeepsTheFullPriceRange() throws Exception {
        mockMvc.perform(get(SAREES).param("priceMin", "500000").param("priceMax", "1000000"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[*].pricePaise", everyItem(org.hamcrest.Matchers.lessThanOrEqualTo(1000000))))
                .andExpect(jsonPath("$.priceRange.maxPaise").value(2490000));
    }

    @Test
    void showsTheProductPageWithBlouseOptions() throws Exception {
        mockMvc.perform(get("/api/v1/store/products/paithani-silk-saree-peacock-pallu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.slug").value("sarees"))
                .andExpect(jsonPath("$.blouseIncluded").value(true))
                .andExpect(jsonPath("$.sareeLengthM").value(5.5))
                .andExpect(jsonPath("$.variants[0].stockLeft").value(2))
                .andExpect(jsonPath("$.attributes[0].code").value("fabric"))
                .andExpect(jsonPath("$.addons[*].code")
                        .value(org.hamcrest.Matchers.contains("blouse-unstitched", "blouse-standard", "blouse-custom")))
                .andExpect(jsonPath("$.addons[2].input").value("MEASUREMENTS"))
                .andExpect(jsonPath("$.addons[2].pricePaise").value(180000));
    }

    @Test
    void hidesDraftsAndUnknownProducts() throws Exception {
        mockMvc.perform(get("/api/v1/store/products/tissue-silk-saree-draft")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/store/categories/nope/products")).andExpect(status().isNotFound());
    }
}

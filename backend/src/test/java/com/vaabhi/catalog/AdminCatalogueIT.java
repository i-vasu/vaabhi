package com.vaabhi.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The admin write side against real Postgres: draft → publish → visible in the store → archive. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class AdminCatalogueIT {

    private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    private static final String FORM = """
            {"name":"Mint organza lehenga","slug":null,"subtitle":"Mirror work","description":"Light and bright",
             "categorySlug":"lehengas","badge":"NEW","careInstructions":"Dry clean only","sareeLengthM":null,
             "blouseIncluded":false,"blouseLengthM":null,
             "attributes":{"fabric":["organza"],"occasion":["festive"]},
             "variants":[
               {"sku":null,"colourName":"Mint","colourHex":"#bfe3d0","colourFamily":"green","size":"M",
                "pricePaise":1500000,"mrpPaise":1800000,"stockQty":2,"active":true},
               {"sku":null,"colourName":"Mint","colourHex":"#bfe3d0","colourFamily":"green","size":"L",
                "pricePaise":1500000,"mrpPaise":1800000,"stockQty":0,"active":true}],
             "addons":[{"code":"blouse-custom","kind":"BLOUSE","name":"Made-to-measure blouse","description":null,
                        "pricePaise":150000,"input":"MEASUREMENTS","active":true}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    private long create(String body) throws Exception {
        var response = mockMvc.perform(post("/api/v1/admin/products")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void draftIsHiddenUntilPublished() throws Exception {
        var id = create(FORM);

        mockMvc.perform(get("/api/v1/admin/products/" + id).with(ADMIN))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.slug").value("mint-organza-lehenga"))
                .andExpect(
                        jsonPath("$.variants[0].sku").value(org.hamcrest.Matchers.matchesPattern("VB-[A-Z2-9]{6}-M")))
                .andExpect(jsonPath("$.variants[0].colourHex").value("#BFE3D0"))
                .andExpect(jsonPath("$.attributes.fabric[0]").value("organza"));
        mockMvc.perform(get("/api/v1/store/products/mint-organza-lehenga")).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/products/" + id + "/publish").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/store/products/mint-organza-lehenga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(2))
                .andExpect(jsonPath("$.addons[0].code").value("blouse-custom"));
        mockMvc.perform(get("/api/v1/store/categories/lehengas/products").param("fabric", "organza"))
                .andExpect(jsonPath("$.items[*].slug", org.hamcrest.Matchers.hasItem("mint-organza-lehenga")));

        mockMvc.perform(post("/api/v1/admin/products/" + id + "/archive").with(ADMIN))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/v1/store/products/mint-organza-lehenga")).andExpect(status().isNotFound());
    }

    @Test
    void updatesKeepSkusAndRejectRemovingTheLastVariantOfAPublishedProduct() throws Exception {
        var id = create(FORM.replace("Mint organza lehenga", "Lilac organza lehenga"));
        var product = mockMvc.perform(get("/api/v1/admin/products/" + id).with(ADMIN))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String sku = JsonPath.read(product, "$.variants[0].sku");

        var edited = FORM.replace("Mint organza lehenga", "Lilac organza lehenga")
                .replaceFirst("\"sku\":null", "\"sku\":\"" + sku + "\"")
                .replaceFirst("\"stockQty\":2", "\"stockQty\":9");
        mockMvc.perform(put("/api/v1/admin/products/" + id)
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edited))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].sku").value(sku))
                .andExpect(jsonPath("$.variants[0].stockQty").value(9));

        mockMvc.perform(post("/api/v1/admin/products/" + id + "/publish").with(ADMIN))
                .andExpect(status().isOk());
        var noVariants = edited.replaceAll("(?s)\"variants\":\\[.*?\\],\\s*\"addons\"", "\"variants\":[],\"addons\"");
        mockMvc.perform(put("/api/v1/admin/products/" + id)
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noVariants))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("variants"));
        mockMvc.perform(get("/api/v1/admin/products/" + id).with(ADMIN))
                .andExpect(jsonPath("$.variants.length()").value(2));
    }

    @Test
    void reportsBusinessRuleViolationsAndConflicts() throws Exception {
        var invalid = FORM.replace("\"categorySlug\":\"lehengas\"", "\"categorySlug\":\"shoes\"")
                .replace("\"organza\"", "\"nylon\"")
                .replaceFirst("\"mrpPaise\":1800000", "\"mrpPaise\":100");
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "categorySlug", "attributes.fabric", "variants[0].mrpPaise")));

        var takenSku = FORM.replaceFirst("\"sku\":null", "\"sku\":\"DEMO-FA748E-1\"");
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(takenSku))
                .andExpect(status().isConflict());
        var takenSlug = FORM.replace("\"slug\":null", "\"slug\":\"kanjeevaram-silk-saree-temple-border\"");
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(takenSlug))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("slug"));
    }

    @Test
    void listsAndSearches() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products").param("status", "DRAFT").with(ADMIN))
                .andExpect(jsonPath("$.items[*].slug", org.hamcrest.Matchers.hasItem("tissue-silk-saree-draft")));
        mockMvc.perform(get("/api/v1/admin/products").param("q", "fa748e").with(ADMIN))
                .andExpect(jsonPath("$.items[0].slug").value("kanjeevaram-silk-saree-temple-border"));
        mockMvc.perform(get("/api/v1/admin/products")
                        .param("category", "dupattas")
                        .with(ADMIN))
                .andExpect(jsonPath("$.total").value(3));
        mockMvc.perform(get("/api/v1/admin/catalog/options").with(ADMIN))
                .andExpect(jsonPath("$.facets[0].code").value("fabric"))
                .andExpect(jsonPath("$.colourFamilies.length()").value(14));
    }
}

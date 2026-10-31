package com.vaabhi.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.shared.security.SecurityConfig;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AdminProductController.class)
@Import(SecurityConfig.class)
class AdminProductControllerTest {

    private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    private static final String VALID = """
            {"name":"Silk saree","slug":null,"subtitle":null,"description":null,"categorySlug":"sarees",
             "badge":null,"careInstructions":null,"sareeLengthM":5.5,"blouseIncluded":true,"blouseLengthM":0.8,
             "attributes":{"fabric":["silk"]},
             "variants":[{"sku":null,"colourName":"Red","colourHex":"#B3261E","colourFamily":"red","size":null,
                          "pricePaise":100000,"mrpPaise":120000,"stockQty":2,"active":true}],
             "addons":[]}
            """;

    private static final AdminProduct PRODUCT = new AdminProduct(
            7,
            "silk-saree",
            "Silk saree",
            null,
            null,
            "sarees",
            ProductStatus.DRAFT,
            null,
            null,
            null,
            false,
            null,
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            OffsetDateTime.parse("2026-10-31T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminProductService service;

    @Test
    void requiresAnAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/products").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void listsWithFilters() throws Exception {
        given(service.list(ProductStatus.DRAFT, "sarees", "silk", 2, 20))
                .willReturn(new AdminProductPage(List.of(), 0, 2, 20));

        mockMvc.perform(get("/api/v1/admin/products?status=DRAFT&category=sarees&q=silk&page=2&pageSize=20")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));
        mockMvc.perform(get("/api/v1/admin/products?pageSize=500").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void createsADraft() throws Exception {
        given(service.create(any())).willReturn(PRODUCT);

        mockMvc.perform(post("/api/v1/admin/products")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void reportsEachInvalidField() throws Exception {
        var invalid = VALID.replace("\"name\":\"Silk saree\"", "\"name\":\"\"")
                .replace("\"colourHex\":\"#B3261E\"", "\"colourHex\":\"red\"")
                .replace("\"pricePaise\":100000", "\"pricePaise\":0");

        mockMvc.perform(put("/api/v1/admin/products/7")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'variants[0].colourHex')].message")
                        .value("must be a colour like #6E2A35"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void changesStatus() throws Exception {
        given(service.changeStatus(eq(7L), any())).willReturn(PRODUCT);

        for (var action : List.of("publish", "unpublish", "archive")) {
            mockMvc.perform(post("/api/v1/admin/products/7/" + action).with(ADMIN))
                    .andExpect(status().isOk());
        }
        verify(service).changeStatus(7, ProductStatus.PUBLISHED);
        verify(service).changeStatus(7, ProductStatus.DRAFT);
        verify(service).changeStatus(7, ProductStatus.ARCHIVED);
    }

    @Test
    void servesFormOptionsAndOneProduct() throws Exception {
        given(service.options()).willReturn(new CatalogOptions(List.of(), List.of(), List.of(), List.of("M")));
        given(service.get(7)).willReturn(PRODUCT);

        mockMvc.perform(get("/api/v1/admin/catalog/options").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizes[0]").value("M"));
        mockMvc.perform(get("/api/v1/admin/products/7").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("silk-saree"));
    }
}

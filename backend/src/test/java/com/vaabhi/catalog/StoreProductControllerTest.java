package com.vaabhi.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.shared.security.SecurityConfig;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreProductController.class)
@Import(SecurityConfig.class)
class StoreProductControllerTest {

    private static final ProductListingResponse EMPTY = new ProductListingResponse(
            new CategoryResponse(1L, "sarees", "Sarees", null), List.of(), 0, 1, 24, "popular", List.of(), null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void parsesFiltersFromTheQueryString() throws Exception {
        given(productService.listProducts(any())).willReturn(EMPTY);

        mockMvc.perform(get("/api/v1/store/categories/sarees/products")
                        .queryParam("fabric", "silk, cotton")
                        .queryParam("fabric", "organza")
                        .queryParam("occasion", "wedding")
                        .queryParam("colour", "red,maroon")
                        .queryParam("size", "M")
                        .queryParam("inStock", "true")
                        .queryParam("priceMin", "100000")
                        .queryParam("sort", "price_desc")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.slug").value("sarees"));

        var captor = ArgumentCaptor.forClass(ListingQuery.class);
        verify(productService).listProducts(captor.capture());
        var query = captor.getValue();
        assertThat(query.categorySlug()).isEqualTo("sarees");
        assertThat(query.facets())
                .containsOnlyKeys("fabric", "occasion")
                .containsEntry("fabric", Set.of("silk", "cotton", "organza"));
        assertThat(query.colours()).containsExactlyInAnyOrder("red", "maroon");
        assertThat(query.sizes()).containsExactly("M");
        assertThat(query.inStock()).isTrue();
        assertThat(query.priceMinPaise()).isEqualTo(100000);
        assertThat(query.priceMaxPaise()).isNull();
        assertThat(query.sort()).isEqualTo(ProductSort.PRICE_DESC);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.pageSize()).isEqualTo(12);
    }

    @Test
    void appliesDefaults() throws Exception {
        given(productService.listProducts(any())).willReturn(EMPTY);

        mockMvc.perform(get("/api/v1/store/categories/sarees/products")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ListingQuery.class);
        verify(productService).listProducts(captor.capture());
        assertThat(captor.getValue())
                .returns(ProductSort.POPULAR, ListingQuery::sort)
                .returns(1, ListingQuery::page)
                .returns(24, ListingQuery::pageSize)
                .returns(false, ListingQuery::inStock);
        assertThat(captor.getValue().facets()).isEmpty();
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        for (var bad : List.of("sort=cheapest", "page=0", "pageSize=49", "priceMin=-1", "priceMin=500&priceMax=100")) {
            mockMvc.perform(get("/api/v1/store/categories/sarees/products?" + bad))
                    .andExpect(status().isBadRequest());
        }
        verify(productService, never()).listProducts(any());
    }

    @Test
    void returnsNotFoundAsProblemDetail() throws Exception {
        given(productService.getProduct("gone")).willThrow(new CatalogNotFoundException("Product", "gone"));

        mockMvc.perform(get("/api/v1/store/products/gone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product 'gone' not found"));
    }
}

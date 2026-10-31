package com.vaabhi.catalog;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.shared.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreCategoryController.class)
@Import(SecurityConfig.class)
class StoreCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void listsCategoriesWithoutAuthentication() throws Exception {
        given(categoryService.listCategories()).willReturn(List.of(new CategoryResponse(1L, "sarees", "Sarees", null)));

        mockMvc.perform(get("/api/v1/store/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sarees"));
    }
}

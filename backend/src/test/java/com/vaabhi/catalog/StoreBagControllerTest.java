package com.vaabhi.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.shared.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreBagController.class)
@Import(SecurityConfig.class)
class StoreBagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BagQuoteService service;

    @Test
    void isPublic() throws Exception {
        given(service.quote(any())).willReturn(new BagQuote(List.of(), 0, 0, 0, 0));

        mockMvc.perform(post("/api/v1/store/bag/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"sku\":\"A\",\"addonCode\":null,\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaise").value(0));
    }

    @Test
    void limitsQuantityToThree() throws Exception {
        mockMvc.perform(post("/api/v1/store/bag/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"sku\":\"A\",\"addonCode\":null,\"quantity\":4}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].quantity"));
        verifyNoInteractions(service);
    }
}

package com.vaabhi.catalog;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Picking a tax class on a category, against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class AdminCategoryControllerIT {

    private static final RequestPostProcessor ADMIN =
            jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private long categoryId() {
        return jdbc.sql("SELECT id FROM category ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
    }

    @Test
    void setAndClearCategoryTaxClass() throws Exception {
        long category = categoryId();
        long taxClass = jdbc.sql("SELECT id FROM tax_class ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();

        mockMvc.perform(put("/api/v1/admin/categories/" + category + "/tax-class")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxClassId\":" + taxClass + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category))
                .andExpect(jsonPath("$.taxClassId").value(taxClass));

        mockMvc.perform(put("/api/v1/admin/categories/" + category + "/tax-class")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxClassId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxClassId").value(nullValue()));
    }

    @Test
    void changingNeedsTheSettingsArea() throws Exception {
        var counter =
                jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("PERM_ORDERS"));
        mockMvc.perform(put("/api/v1/admin/categories/" + categoryId() + "/tax-class")
                        .with(counter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxClassId\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownTaxClassIs400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/categories/" + categoryId() + "/tax-class")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxClassId\":999999}"))
                .andExpect(status().isBadRequest());
    }
}

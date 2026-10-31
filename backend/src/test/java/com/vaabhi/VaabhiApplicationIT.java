package com.vaabhi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Full application against a real Postgres. Skipped when Docker is unavailable; always runs in CI. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VaabhiApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthIsReadableFromTheAdminOrigin() throws Exception {
        mockMvc.perform(get("/actuator/health").header("Origin", "http://localhost:3001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3001"));
    }

    @Test
    void infoReportsReleaseVersionAndCommit() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").isNotEmpty())
                .andExpect(jsonPath("$.git.commit.id").isNotEmpty());
    }

    @Test
    void listsSeededCategories() throws Exception {
        mockMvc.perform(get("/api/v1/store/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("sarees"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void adminApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/anything")).andExpect(status().isUnauthorized());
    }
}

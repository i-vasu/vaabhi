package com.vaabhi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Fails when the API changes without the frontend's copy of the spec being updated, so the
 * generated TypeScript types never drift from the backend. To accept a change, run
 * {@code ./mvnw verify -Dopenapi.update=true} and regenerate the client types.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIT {

    static final Path COMMITTED_SPEC = Path.of("..", "frontend", "packages", "api-client", "openapi.json");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void committedSpecMatchesTheRunningApi() throws Exception {
        var body =
                mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        var actual = withoutServers(JSON.readTree(body));

        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(
                    COMMITTED_SPEC, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }

        var committed = withoutServers(JSON.readTree(Files.readString(COMMITTED_SPEC)));
        assertThat(actual)
                .as("API spec changed: run ./mvnw verify -Dopenapi.update=true, then pnpm generate:api in frontend/")
                .isEqualTo(committed);
    }

    /** The server URL depends on the port the test happens to use. */
    private static JsonNode withoutServers(JsonNode spec) {
        ((ObjectNode) spec).remove("servers");
        return spec;
    }
}

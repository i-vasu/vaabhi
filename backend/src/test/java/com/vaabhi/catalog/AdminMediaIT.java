package com.vaabhi.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.storage.ObjectStorage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Photo upload flow against real Postgres, with R2 replaced by an in-memory store. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import({TestcontainersConfiguration.class, AdminMediaIT.FakeStorage.class})
class AdminMediaIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeStorage {
        @Bean
        @Primary
        InMemoryObjectStorage inMemoryObjectStorage() {
            return new InMemoryObjectStorage();
        }
    }

    private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    private static final String SLUG = "jamdani-cotton-saree";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryObjectStorage storage;

    @Autowired
    private JdbcClient jdbc;

    private long productId() {
        return jdbc.sql("SELECT id FROM product WHERE slug = :slug")
                .param("slug", SLUG)
                .query(Long.class)
                .single();
    }

    private long startUpload(long productId, String alt, String colour) throws Exception {
        var body = mockMvc.perform(post("/api/v1/admin/products/" + productId + "/media/uploads")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alt\":\"" + alt + "\",\"colourName\":\"" + colour
                                + "\",\"width\":1600,\"height\":2133}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media.status").value("PENDING"))
                .andExpect(jsonPath("$.renditions[*].width").value(org.hamcrest.Matchers.contains(400, 800, 1600)))
                .andExpect(jsonPath(
                        "$.renditions[0].uploadUrl", startsWith("https://r2.test/products/" + productId + "/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.media.id")).longValue();
    }

    private String keyOf(long productId, long mediaId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/admin/products/" + productId).with(ADMIN))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> keys = JsonPath.read(body, "$.media[?(@.id == " + mediaId + ")].key");
        return keys.getFirst();
    }

    @Test
    void uploadCompletesOnlyWhenEveryRenditionArrivedThenShowsInTheStore() throws Exception {
        var productId = productId();
        var first = startUpload(productId, "White jamdani saree, front", "White");
        var key = keyOf(productId, first);

        mockMvc.perform(post("/api/v1/admin/products/" + productId + "/media/" + first + "/complete")
                        .with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Upload incomplete: missing 400, 800, 1600 px versions"));
        mockMvc.perform(get("/api/v1/store/products/" + SLUG))
                .andExpect(jsonPath("$.media.length()").value(0));

        storage.put(key + "/400.webp");
        storage.put(key + "/800.webp");
        storage.put(key + "/1600.webp");
        mockMvc.perform(post("/api/v1/admin/products/" + productId + "/media/" + first + "/complete")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("READY"));

        mockMvc.perform(get("/api/v1/store/products/" + SLUG))
                .andExpect(jsonPath("$.media[0].key").value(key))
                .andExpect(jsonPath("$.media[0].alt").value("White jamdani saree, front"))
                .andExpect(jsonPath("$.media[0].colourName").value("White"));
        mockMvc.perform(get("/api/v1/store/categories/sarees/products").param("weave", "jamdani"))
                .andExpect(jsonPath("$.items[0].image.key").value(key));

        // A second photo, reordered first, then the first one deleted (its files go too).
        var second = startUpload(productId, "Black jamdani saree", "Black");
        mockMvc.perform(put("/api/v1/admin/products/" + productId + "/media")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":" + second + ",\"alt\":\"Black, pallu\",\"colourName\":\"Black\"},"
                                + "{\"id\":" + first + ",\"alt\":\"White, front\",\"colourName\":null}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].alt").value("White, front"))
                .andExpect(jsonPath("$[1].colourName").doesNotExist());
        mockMvc.perform(put("/api/v1/admin/products/" + productId + "/media")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":" + second + ",\"alt\":\"Only one\",\"colourName\":null}]"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/admin/products/" + productId + "/media/" + first)
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(storage.objects).noneMatch(k -> k.startsWith(key));
        mockMvc.perform(delete("/api/v1/admin/products/" + productId + "/media/" + first)
                        .with(ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesUploadsWhenStorageIsNotConfiguredOrTheProductIsUnknown() throws Exception {
        var request = "{\"alt\":\"x\",\"colourName\":null,\"width\":10,\"height\":10}";
        storage.configured = false;
        try {
            mockMvc.perform(post("/api/v1/admin/products/" + productId() + "/media/uploads")
                            .with(ADMIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            storage.configured = true;
        }
        mockMvc.perform(post("/api/v1/admin/products/999999/media/uploads")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/products/" + productId() + "/media/uploads")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alt\":\"\",\"colourName\":null,\"width\":0,\"height\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        mockMvc.perform(post("/api/v1/admin/products/" + productId() + "/media/uploads")
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void capsPhotosPerProduct() throws Exception {
        var productId = jdbc.sql("SELECT id FROM product WHERE slug = 'crepe-saree-office'")
                .query(Long.class)
                .single();
        for (var i = 0; i < ProductMediaService.MAX_PHOTOS; i++) {
            startUpload(productId, "Photo " + i, "Grey");
        }
        mockMvc.perform(post("/api/v1/admin/products/" + productId + "/media/uploads")
                        .with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alt\":\"one too many\",\"colourName\":null,\"width\":10,\"height\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("media"));
    }

    @Test
    void exposesTheStorageBeanUsedByTheApp(@Autowired ObjectStorage objectStorage) {
        assertThat(objectStorage).isSameAs(storage);
    }
}

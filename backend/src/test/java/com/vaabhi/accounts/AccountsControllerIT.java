package com.vaabhi.accounts;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

/** The accounts admin API and who may call it, against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class AccountsControllerIT {

    private static final RequestPostProcessor OWNER =
            jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));
    private static final RequestPostProcessor MONEY_STAFF =
            jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("PERM_MONEY"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private List<Long> twoAccounts() {
        return jdbc.sql("SELECT id FROM account WHERE active ORDER BY code LIMIT 2")
                .query(Long.class)
                .list();
    }

    private String entryJson(String narration, String creditAmount) {
        var accounts = twoAccounts();
        return """
                {"date":"%s","narration":"%s","lines":[
                  {"accountId":%d,"debitRupees":100.00,"creditRupees":0},
                  {"accountId":%d,"debitRupees":0,"creditRupees":%s}
                ]}
                """.formatted(
                LocalDate.now(ZoneId.of("Asia/Kolkata")), narration, accounts.get(0), accounts.get(1), creditAmount);
    }

    @Test
    void staffWithMoneyCanRead() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/accounts").with(MONEY_STAFF))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/accounts/trial-balance").with(MONEY_STAFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").isBoolean());
        mockMvc.perform(get("/api/v1/admin/accounts/journal").with(MONEY_STAFF)).andExpect(status().isOk());
    }

    @Test
    void staffWithoutOwnerCannotPostManual() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/journal")
                        .with(MONEY_STAFF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("Staff attempt", "100.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerPostsAndReversesManualEntry() throws Exception {
        var created = mockMvc.perform(post("/api/v1/admin/accounts/journal")
                        .with(OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("IT manual entry", "100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.narration").value("IT manual entry"))
                .andReturn();
        var number =
                com.jayway.jsonpath.JsonPath.<String>read(created.getResponse().getContentAsString(), "$.number");
        var id = com.jayway.jsonpath.JsonPath.<Number>read(created.getResponse().getContentAsString(), "$.id")
                .longValue();

        mockMvc.perform(get("/api/v1/admin/accounts/journal?q=IT manual entry&size=100")
                        .with(MONEY_STAFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].number").value(hasItem(number)));

        mockMvc.perform(post("/api/v1/admin/accounts/journal/" + id + "/reverse")
                        .with(OWNER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceStep").value("REVERSAL"))
                .andExpect(jsonPath("$.reversesId").value(id));
    }

    @Test
    void manualUnbalancedIs400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts/journal")
                        .with(OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("IT unbalanced", "90.00")))
                .andExpect(status().isBadRequest());
    }
}

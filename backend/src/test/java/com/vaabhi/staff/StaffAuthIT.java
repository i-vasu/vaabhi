package com.vaabhi.staff;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Staff sign-in against real Postgres: the demo owner, wrong passwords, lockout and the issued token. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class StaffAuthIT {

    private static final String OWNER_EMAIL = "owner@vaabhi.local";
    private static final String OWNER_PASSWORD = "vaabhi-demo-owner";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccountStore store;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/staff/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    @Test
    void demoOwnerSignsInAndTheTokenOpensTheAdminApi() throws Exception {
        var response = login("Owner@Vaabhi.local", OWNER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(43_200))
                .andExpect(jsonPath("$.staff.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.staff.role").value("OWNER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(response, "$.accessToken");

        mockMvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Demo Owner"))
                .andExpect(jsonPath("$.role").value("OWNER"));
        mockMvc.perform(get("/api/v1/admin/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameAnswer() throws Exception {
        login("nobody@vaabhi.local", OWNER_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is wrong"));
        login("someone-else@vaabhi.local", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is wrong"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        var email = "tailor-lock@vaabhi.local";
        store.create("Tailor", email, null, StaffRole.STAFF, passwordEncoder.encode("right-password"));

        for (int i = 0; i < 5; i++) {
            login(email, "wrong-" + i).andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while locked.
        login(email, "right-password").andExpect(status().isTooManyRequests());
    }

    @Test
    void switchedOffAccountCannotSignIn() throws Exception {
        var email = "former@vaabhi.local";
        var id = store.create("Former", email, null, StaffRole.STAFF, passwordEncoder.encode("right-password"));
        store.setActive(id, false);

        login(email, "right-password").andExpect(status().isForbidden());
    }

    @Test
    void staffChangeTheirOwnPassword() throws Exception {
        var email = "tailor-password@vaabhi.local";
        var id = store.create("Tailor", email, null, StaffRole.STAFF, passwordEncoder.encode("old-password-1"));
        var me = jwt().jwt(j -> j.subject("staff:" + id)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        mockMvc.perform(put("/api/v1/admin/me/password")
                        .with(me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is wrong"));
        mockMvc.perform(put("/api/v1/admin/me/password")
                        .with(me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password-1\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/me/password")
                        .with(me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password-1\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());

        login(email, "old-password-1").andExpect(status().isUnauthorized());
        login(email, "new-password-1").andExpect(status().isOk());
    }

    @Test
    void rejectsMalformedRequests() throws Exception {
        login("not-an-email", "x").andExpect(status().isBadRequest());
    }

    @Test
    void meRefusesTokensThatAreNotStaffSignIns() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me")
                        .with(jwt().jwt(j -> j.subject("script:vasu"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/me")
                        .with(jwt().jwt(j -> j.subject("staff:999999"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/me")
                        .with(jwt().jwt(j -> j.subject("staff:abc"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }
}

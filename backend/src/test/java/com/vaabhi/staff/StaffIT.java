package com.vaabhi.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Staff, roles and permissions, attendance and requests against real Postgres, with real sign-ins so the
 * permissions in the token are what the API checks.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class StaffIT {

    private static final String PASSWORD = "a-long-password-1";

    @Autowired
    private MockMvc mockMvc;

    private String login(String email, String password) throws Exception {
        var body = mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String owner() throws Exception {
        return login("owner@vaabhi.local", "vaabhi-demo-owner");
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return as(token, request, "");
    }

    private static String email() {
        return UUID.randomUUID() + "@vaabhi.test";
    }

    private static String form(String name, String role, String email, String password) {
        return """
                {"name":"%s","phone":"9876512345","email":%s,"role":"%s","designation":"Tailor",
                 "joinedOn":"2026-01-15","payType":"MONTHLY","salaryPaise":1500000,"hourlyPaise":12000,
                 "shiftStart":"10:00","shiftEnd":"19:00","lateGraceMinutes":10,"weeklyOff":3,"password":%s}
                """.formatted(
                        name,
                        email == null ? "null" : "\"" + email + "\"",
                        role,
                        password == null ? "null" : "\"" + password + "\"");
    }

    private long create(String token, String name, String role, String email, String password) throws Exception {
        var body = as(token, post("/api/v1/admin/staff"), form(name, role, email, password))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // --- staff and roles ----------------------------------------------------------------------------------------

    @Test
    void ownerAddsAManagerWhoseTokenCarriesTheRolesPermissions() throws Exception {
        var owner = owner();
        var managerEmail = email();
        long id = create(owner, "Meena Manager", "MANAGER", managerEmail, PASSWORD);
        as(owner, get("/api/v1/admin/staff/" + id))
                .andExpect(jsonPath("$.canSignIn").value(true))
                .andExpect(jsonPath("$.weeklyOff").value(3))
                .andExpect(jsonPath("$.shiftStart").value("10:00:00"));

        var signIn = mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(managerEmail, PASSWORD)))
                .andExpect(jsonPath("$.staff.role").value("MANAGER"))
                .andExpect(jsonPath("$.staff.permissions")
                        .value(org.hamcrest.Matchers.hasItems("ORDERS", "WORKSHOP", "MONEY", "STAFF", "CATALOGUE")))
                .andReturn();
        String manager = JsonPath.read(signIn.getResponse().getContentAsString(), "$.accessToken");

        as(manager, get("/api/v1/admin/money/ledger")).andExpect(status().isOk());
        as(manager, get("/api/v1/admin/staff")).andExpect(status().isOk());
        as(manager, get("/api/v1/admin/settings/billing")).andExpect(status().isOk());
        as(manager, get("/api/v1/admin/payroll/months/2026-01")).andExpect(status().isForbidden());
        as(manager, put("/api/v1/admin/settings/watermark"), "{}").andExpect(status().isForbidden());
        as(manager, put("/api/v1/admin/staff/roles/STAFF"), "{\"permissions\":[\"MONEY\"]}")
                .andExpect(status().isForbidden());
        as(manager, get("/api/v1/admin/me"))
                .andExpect(jsonPath("$.permissions")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("PAYROLL"))));

        // Managers manage staff, not other managers or owners.
        as(manager, post("/api/v1/admin/staff"), form("Another Manager", "MANAGER", email(), null))
                .andExpect(status().isForbidden());
        long tailor = create(manager, "Tara Tailor", "STAFF", null, null);
        // Not themselves either: only the owner manages managers.
        as(manager, post("/api/v1/admin/staff/" + id + "/deactivate")).andExpect(status().isForbidden());
        as(manager, post("/api/v1/admin/staff/" + tailor + "/deactivate"))
                .andExpect(jsonPath("$.active").value(false));
        as(manager, get("/api/v1/admin/staff"))
                .andExpect(jsonPath("$[?(@.id == " + tailor + ")]").isEmpty());
        as(manager, get("/api/v1/admin/staff").param("includeInactive", "true"))
                .andExpect(jsonPath("$[?(@.id == " + tailor + ")].active").value(false));

        // Taking the sign-in away stops the next sign-in.
        as(owner, delete("/api/v1/admin/staff/" + id + "/sign-in"))
                .andExpect(jsonPath("$.canSignIn").value(false));
        mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(managerEmail, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffWithoutASignInNeedNoEmailButAPasswordNeedsOne() throws Exception {
        var owner = owner();
        long id = create(owner, "Gopi", "STAFF", null, null);
        as(owner, get("/api/v1/admin/staff/" + id))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.canSignIn").value(false));
        as(owner, post("/api/v1/admin/staff"), form("Hari", "STAFF", null, PASSWORD))
                .andExpect(status().isBadRequest());
        as(owner, post("/api/v1/admin/staff"), form("Hari", "STAFF", email(), "short"))
                .andExpect(status().isBadRequest());
        as(
                        owner,
                        put("/api/v1/admin/staff/" + id),
                        form("Gopi", "STAFF", null, null).replace("\"shiftEnd\":\"19:00\"", "\"shiftEnd\":\"09:00\""))
                .andExpect(status().isBadRequest());
        var duplicate = email();
        create(owner, "Indu", "STAFF", duplicate, null);
        as(owner, post("/api/v1/admin/staff"), form("Indu 2", "STAFF", duplicate.toUpperCase(), null))
                .andExpect(status().isConflict());
    }

    @Test
    void theOwnerCannotSwitchThemselvesOff() throws Exception {
        var owner = owner();
        var me = as(owner, get("/api/v1/admin/me")).andReturn().getResponse().getContentAsString();
        long ownerId = ((Number) JsonPath.read(me, "$.id")).longValue();
        assertThat(JsonPath.<List<String>>read(me, "$.permissions")).contains("PAYROLL", "SETTINGS");
        as(owner, post("/api/v1/admin/staff/" + ownerId + "/deactivate")).andExpect(status().isBadRequest());
    }

    @Test
    void ownerChangesWhatARoleMayDo() throws Exception {
        var owner = owner();
        var email = email();
        create(owner, "Jaya", "STAFF", email, PASSWORD);
        try {
            as(owner, put("/api/v1/admin/staff/roles/STAFF"), "{\"permissions\":[\"WORKSHOP\",\"MONEY\"]}")
                    .andExpect(jsonPath("$.permissions")
                            .value(org.hamcrest.Matchers.containsInAnyOrder("WORKSHOP", "MONEY")));
            as(owner, put("/api/v1/admin/staff/roles/OWNER"), "{\"permissions\":[]}")
                    .andExpect(status().isBadRequest());
            var staff = login(email, PASSWORD);
            as(staff, get("/api/v1/admin/money/ledger")).andExpect(status().isOk());
            as(staff, get("/api/v1/admin/orders")).andExpect(status().isForbidden());
            as(owner, get("/api/v1/admin/staff/roles"))
                    .andExpect(jsonPath("$[?(@.role == 'OWNER')].editable").value(false));
        } finally {
            as(owner, put("/api/v1/admin/staff/roles/STAFF"), "{\"permissions\":[\"ORDERS\",\"WORKSHOP\"]}")
                    .andExpect(status().isOk());
        }
    }

    // --- attendance ---------------------------------------------------------------------------------------------

    @Test
    void marksDaysWithLateAndOvertimeAndShowsTheMonth() throws Exception {
        var owner = owner();
        long id = create(owner, "Kamala", "STAFF", null, null);
        // 2001-07-04 was a Wednesday, Kamala's weekly off (3); the shop itself is open (SettingsIT closes Sundays).
        as(owner, get("/api/v1/admin/staff/attendance").param("day", "2001-07-04"))
                .andExpect(jsonPath("$.entries[?(@.staffId == " + id + ")].suggested")
                        .value("OFF"))
                .andExpect(jsonPath("$.entries[?(@.staffId == " + id + ")].status")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));

        as(owner, put("/api/v1/admin/staff/attendance/2001-07-02/" + id), """
                        {"status":"PRESENT","checkIn":"10:25","checkOut":"20:30","note":"Wedding rush"}
                        """)
                .andExpect(jsonPath("$.entries[?(@.staffId == " + id + ")].lateMinutes")
                        .value(25))
                .andExpect(jsonPath("$.entries[?(@.staffId == " + id + ")].overtimeMinutes")
                        .value(90))
                .andExpect(jsonPath("$.entries[?(@.staffId == " + id + ")].markedBy")
                        .value("Demo Owner"));
        as(owner, put("/api/v1/admin/staff/attendance/2001-07-03/" + id), """
                        {"status":"ABSENT","checkIn":"10:00","checkOut":null,"note":null}
                        """)
                .andExpect(jsonPath("$.entries[?(@.staffId == " + id + ")].checkIn")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        as(owner, put("/api/v1/admin/staff/attendance/2001-07-04/" + id), """
                        {"status":"PRESENT","checkIn":"12:00","checkOut":"11:00","note":null}
                        """).andExpect(status().isBadRequest());
        as(owner, post("/api/v1/admin/staff/attendance/2001-07-05"), """
                        {"status":"HALF_DAY","staffIds":[%d]}
                        """.formatted(id))
                .andExpect(status().isOk());

        var month = as(owner, get("/api/v1/admin/staff/attendance/month/2001-07"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Object> row = JsonPath.read(month, "$.rows[?(@.staffId == " + id + ")]");
        assertThat(row).hasSize(1);
        String path = "$.rows[?(@.staffId == " + id + ")].";
        assertThat(JsonPath.<List<Integer>>read(month, path + "present")).containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(month, path + "absent")).containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(month, path + "halfDays")).containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(month, path + "unmarked")).containsExactly(28);
        assertThat(JsonPath.<List<Integer>>read(month, path + "lateMinutes")).containsExactly(25);
        List<List<String>> days = JsonPath.read(month, path + "days");
        assertThat(days.getFirst()).hasSize(31);
        assertThat(days.getFirst().get(1)).isEqualTo("PRESENT");

        as(owner, delete("/api/v1/admin/staff/attendance/2001-07-03/" + id))
                .andExpect(jsonPath("$.entries[?(@.staffId == " + id + ")].status")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        as(owner, get("/api/v1/admin/staff/attendance/month/July")).andExpect(status().isBadRequest());
    }

    // --- requests -----------------------------------------------------------------------------------------------

    @Test
    void staffRaiseTheirOwnRequestsAndSomeoneElseDecides() throws Exception {
        var owner = owner();
        var email = email();
        long id = create(owner, "Lakshmi", "STAFF", email, PASSWORD);
        var staff = login(email, PASSWORD);

        var leave = as(staff, post("/api/v1/admin/me/requests"), """
                        {"staffId":1,"kind":"LEAVE","fromDate":"2001-08-06","toDate":"2001-08-08","reason":"Family function"}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffId").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long leaveId = ((Number) JsonPath.read(leave, "$.id")).longValue();
        as(staff, get("/api/v1/admin/me/requests"))
                .andExpect(jsonPath("$[0].id").value(leaveId));

        // Staff cannot see or decide everyone's requests (no STAFF permission).
        as(staff, get("/api/v1/admin/staff/requests")).andExpect(status().isForbidden());
        as(staff, post("/api/v1/admin/me/requests"), """
                        {"kind":"ADVANCE","fromDate":"2001-08-01","reason":"School fees"}
                        """).andExpect(status().isBadRequest());
        as(staff, post("/api/v1/admin/me/requests"), """
                        {"kind":"LEAVE","fromDate":"2001-08-01","toDate":"2001-09-15"}
                        """).andExpect(status().isBadRequest());

        as(owner, put("/api/v1/admin/staff/attendance/2001-08-07/" + id), """
                        {"status":"PRESENT","checkIn":"10:00","checkOut":"19:00","note":null}
                        """).andExpect(status().isOk());
        as(owner, post("/api/v1/admin/staff/requests/" + leaveId + "/decision"), "{\"approve\":true,\"note\":\"OK\"}")
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("Demo Owner"));
        as(owner, post("/api/v1/admin/staff/requests/" + leaveId + "/decision"), "{\"approve\":false,\"note\":null}")
                .andExpect(status().isBadRequest());

        // Leave marks the days, but not the day already marked worked.
        for (var day : List.of("2001-08-06", "2001-08-08")) {
            as(owner, get("/api/v1/admin/staff/attendance").param("day", day))
                    .andExpect(jsonPath("$.entries[?(@.staffId == " + id + ")].status")
                            .value("LEAVE"));
        }
        as(owner, get("/api/v1/admin/staff/attendance").param("day", "2001-08-07"))
                .andExpect(
                        jsonPath("$.entries[?(@.staffId == " + id + ")].status").value("PRESENT"));

        // Nobody decides their own request.
        var ownRequest = as(owner, post("/api/v1/admin/me/requests"), """
                        {"kind":"PERMISSION","fromDate":"2001-08-10","hours":2}
                        """)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        as(
                        owner,
                        post("/api/v1/admin/staff/requests/" + JsonPath.read(ownRequest, "$.id") + "/decision"),
                        "{\"approve\":true,\"note\":null}")
                .andExpect(status().isForbidden());

        as(owner, get("/api/v1/admin/staff/requests").param("staffId", Long.toString(id)))
                .andExpect(jsonPath("$[0].id").value(leaveId));
    }
}

package com.vaabhi.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Adding or changing a signed-in customer's mobile number, end to end against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "vaabhi.auth.google-client-id=" + CustomerAuthIT.CLIENT_ID)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CustomerAuthIT.Fakes.class})
class PhoneChangeIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerAuthIT.Fakes.CapturingWhatsApp whatsApp;

    @Autowired
    private AuthProperties properties;

    private ResultActions postJson(String path, String body, String access) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions putJson(String path, String body, String access) throws Exception {
        return mockMvc.perform(put(path)
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Signs up a phone customer and returns their access token. */
    private String phoneAccessToken(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isAccepted());
        var code = whatsApp.codes.get(PhoneNumbers.normalize(phone).orElseThrow());
        var session = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(session, "$.accessToken");
    }

    /** Signs in a Google customer and returns their access token. */
    private String googleAccessToken(String credential) throws Exception {
        var session = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + credential + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(session, "$.accessToken");
    }

    private String requestChangeCode(String access, String phone) throws Exception {
        postJson("/api/v1/customer/me/phone/otp", "{\"phone\":\"" + phone + "\"}", access)
                .andExpect(status().isAccepted());
        return whatsApp.codes.get(PhoneNumbers.normalize(phone).orElseThrow());
    }

    @Test
    void googleCustomerAddsANumberAndPhoneCustomerChangesTheirs() throws Exception {
        // A Google customer has no phone yet; they add one.
        var googleAccess = googleAccessToken("google:sub-pc1:phonechange@example.com");
        var code = requestChangeCode(googleAccess, "7100000001");
        assertThat(code).matches("\\d{6}");
        putJson("/api/v1/customer/me/phone", "{\"phone\":\"+91 71000-00001\",\"code\":\"" + code + "\"}", googleAccess)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+917100000001"));

        // A phone customer changes their existing number to a new one.
        var phoneAccess = phoneAccessToken("7100000002");
        var newCode = requestChangeCode(phoneAccess, "7100000003");
        putJson("/api/v1/customer/me/phone", "{\"phone\":\"7100000003\",\"code\":\"" + newCode + "\"}", phoneAccess)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+917100000003"));
    }

    @Test
    void refusesANumberAlreadyOnAnotherAccount() throws Exception {
        var access = phoneAccessToken("7100000004");
        phoneAccessToken("7100000005");
        postJson("/api/v1/customer/me/phone/otp", "{\"phone\":\"7100000005\"}", access)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This number is already on another account"));
    }

    @Test
    void refusesTheCustomersOwnNumber() throws Exception {
        var access = phoneAccessToken("7100000006");
        postJson("/api/v1/customer/me/phone/otp", "{\"phone\":\"7100000006\"}", access)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.detail").value("This is already your number"));
    }

    @Test
    void countsWrongCodesLikeSignIn() throws Exception {
        var access = phoneAccessToken("7100000007");
        var code = requestChangeCode(access, "7100000008");
        var wrong = code.equals("000000") ? "111111" : "000000";
        putJson("/api/v1/customer/me/phone", "{\"phone\":\"7100000008\",\"code\":\"" + wrong + "\"}", access)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.detail").value("Incorrect code. 4 tries left."));
        // The right code still works after one wrong try.
        putJson("/api/v1/customer/me/phone", "{\"phone\":\"7100000008\",\"code\":\"" + code + "\"}", access)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+917100000008"));
    }

    @Test
    void signInCodeCannotChangeANumber() throws Exception {
        var access = phoneAccessToken("7100000009");
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"7100000010\"}"))
                .andExpect(status().isAccepted());
        var signInCode = whatsApp.codes.get("+917100000010");
        putJson("/api/v1/customer/me/phone", "{\"phone\":\"7100000010\",\"code\":\"" + signInCode + "\"}", access)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This code has expired. Send a new one."));
    }

    @Test
    void rejectsAnInvalidNumber() throws Exception {
        var access = phoneAccessToken("7100000011");
        postJson("/api/v1/customer/me/phone/otp", "{\"phone\":\"12345\"}", access)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.detail").value("Enter a valid 10-digit Indian mobile number"));
    }

    @Test
    void waitsBeforeSendingAnotherCodeToTheSameNumber() throws Exception {
        var access = phoneAccessToken("7100000012");
        requestChangeCode(access, "7100000013");
        postJson("/api/v1/customer/me/phone/otp", "{\"phone\":\"7100000013\"}", access)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30));
    }

    @Test
    void verifyWithNoCodeSentFails() throws Exception {
        var access = phoneAccessToken("7100000014");
        putJson("/api/v1/customer/me/phone", "{\"phone\":\"7100000015\",\"code\":\"123456\"}", access)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.detail").value("This code has expired. Send a new one."));
    }

    @Test
    void tooManyWrongCodesLocksTheChange() throws Exception {
        var access = phoneAccessToken("7100000016");
        var code = requestChangeCode(access, "7100000017");
        var wrong = code.equals("000000") ? "111111" : "000000";
        var maxAttempts = properties.otp().maxAttempts();
        for (var i = 0; i < maxAttempts; i++) {
            putJson("/api/v1/customer/me/phone", "{\"phone\":\"7100000017\",\"code\":\"" + wrong + "\"}", access)
                    .andExpect(status().isBadRequest());
        }
        // Even the right code no longer works once the tries are used up.
        putJson("/api/v1/customer/me/phone", "{\"phone\":\"7100000017\",\"code\":\"" + code + "\"}", access)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Too many wrong tries. Send a new code."));
    }
}

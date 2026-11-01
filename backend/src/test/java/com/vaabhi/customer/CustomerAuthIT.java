package com.vaabhi.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.sms.SmsSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Sign-in end to end against real Postgres: OTP, Google, refresh rotation, logout and the profile. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "vaabhi.auth.google-client-id=" + CustomerAuthIT.CLIENT_ID)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CustomerAuthIT.Fakes.class})
class CustomerAuthIT {

    static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fakes.CapturingSms sms;

    @Autowired
    private Fakes.MovableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {

        /** Remembers the last code sent to each number instead of sending it. */
        static class CapturingSms implements SmsSender {
            final Map<String, String> codes = new ConcurrentHashMap<>();

            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public void send(String phone, String template, Map<String, String> variables) {
                codes.put(phone, variables.get("otp"));
            }
        }

        /** A clock the test moves forward; starts now so tokens are valid for the real JWT decoder. */
        static class MovableClock extends Clock {
            private final AtomicLong offsetMillis = new AtomicLong();

            void advance(Duration duration) {
                offsetMillis.addAndGet(duration.toMillis());
            }

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.now().plusMillis(offsetMillis.get());
            }
        }

        @Bean
        @Primary
        CapturingSms capturingSms() {
            return new CapturingSms();
        }

        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }

        /** Google ID tokens: "google:<sub>:<email>" decodes to a token for our client id; anything else is bad. */
        @Bean
        @Primary
        GoogleIdTokens fakeGoogle(AuthProperties properties) {
            return new GoogleIdTokens(properties, token -> {
                var parts = token.split(":");
                if (parts.length != 3 || !parts[0].equals("google")) {
                    throw new JwtException("bad signature");
                }
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .claim("iss", "https://accounts.google.com")
                        .audience(List.of(parts[2].startsWith("other") ? "someone-else" : CLIENT_ID))
                        .subject(parts[1])
                        .claim("email", parts[2])
                        .claim("email_verified", true)
                        .claim("name", "Meera Iyer")
                        .build();
            });
        }
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String requestCode(String phone) throws Exception {
        postJson("/api/v1/auth/otp/request", "{\"phone\":\"" + phone + "\"}").andExpect(status().isAccepted());
        return sms.codes.get(PhoneNumbers.normalize(phone).orElseThrow());
    }

    private String verify(String phone, String code) throws Exception {
        return postJson("/api/v1/auth/otp/verify", "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void phoneSignInCreatesTheAccountOnceAndUnlocksTheProfile() throws Exception {
        var code = requestCode("98765 43210");
        assertThat(code).matches("\\d{6}");

        postJson("/api/v1/auth/otp/verify", "{\"phone\":\"9876543210\",\"code\":\"" + wrong(code) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.detail").value("Incorrect code. 4 tries left."));

        var session = verify("+91 98765-43210", code);
        assertThat((Boolean) JsonPath.read(session, "$.newCustomer")).isTrue();
        assertThat((String) JsonPath.read(session, "$.customer.phone")).isEqualTo("+919876543210");
        assertThat((Integer) JsonPath.read(session, "$.accessTokenExpiresIn")).isEqualTo(900);
        String access = JsonPath.read(session, "$.accessToken");

        // The code is single-use.
        postJson("/api/v1/auth/otp/verify", "{\"phone\":\"9876543210\",\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This code has expired. Send a new one."));

        mockMvc.perform(get("/api/v1/customer/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+919876543210"))
                .andExpect(jsonPath("$.name").doesNotExist());
        mockMvc.perform(put("/api/v1/customer/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Priya  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya"));

        clock.advance(Duration.ofMinutes(1));
        var again = verify("9876543210", requestCode("9876543210"));
        assertThat((Boolean) JsonPath.read(again, "$.newCustomer")).isFalse();
        assertThat((String) JsonPath.read(again, "$.customer.name")).isEqualTo("Priya");
    }

    @Test
    void limitsResendsAndWrongGuesses() throws Exception {
        var code = requestCode("9123456780");
        postJson("/api/v1/auth/otp/request", "{\"phone\":\"9123456780\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30));

        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/otp/verify", "{\"phone\":\"9123456780\",\"code\":\"" + wrong(code) + "\"}")
                    .andExpect(status().isBadRequest());
        }
        // Even the right code no longer works once the tries are used up.
        postJson("/api/v1/auth/otp/verify", "{\"phone\":\"9123456780\",\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Too many wrong tries. Send a new code."));

        // Expired codes stop working too.
        clock.advance(Duration.ofSeconds(31));
        var fresh = requestCode("9123456780");
        clock.advance(Duration.ofMinutes(6));
        postJson("/api/v1/auth/otp/verify", "{\"phone\":\"9123456780\",\"code\":\"" + fresh + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This code has expired. Send a new one."));
    }

    @Test
    void rejectsNumbersOutsideIndia() throws Exception {
        postJson("/api/v1/auth/otp/request", "{\"phone\":\"+44 7700 900123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    void refreshRotatesAndReuseEndsEverySession() throws Exception {
        var session = verify("9000000001", requestCode("9000000001"));
        String first = JsonPath.read(session, "$.refreshToken");

        var rotated = postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + first + "\"}")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = JsonPath.read(rotated, "$.refreshToken");
        assertThat(second).isNotEqualTo(first);

        // Two tabs refreshing at once: the just-rotated token still works within the grace period.
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + first + "\"}").andExpect(status().isOk());

        // Later reuse of a rotated token looks like theft: every session of the customer ends.
        clock.advance(Duration.ofMinutes(5));
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + first + "\"}").andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + second + "\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSessionAndExpiredSessionsCannotRefresh() throws Exception {
        var session = verify("9000000002", requestCode("9000000002"));
        String refresh = JsonPath.read(session, "$.refreshToken");

        postJson("/api/v1/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}")
                .andExpect(status().isNoContent());
        postJson("/api/v1/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}")
                .andExpect(status().isNoContent());
        // Immediately: a logged-out token gets no grace period.
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}")
                .andExpect(status().isUnauthorized());

        clock.advance(Duration.ofSeconds(31));
        var other = verify("9000000003", requestCode("9000000003"));
        String stale = JsonPath.read(other, "$.refreshToken");
        clock.advance(Duration.ofDays(61));
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + stale + "\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your session has expired. Please sign in again."));
    }

    @Test
    void googleSignInCreatesOrFindsTheAccount() throws Exception {
        var created = postJson("/api/v1/auth/google", "{\"credential\":\"google:sub-1:meera@example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCustomer").value(true))
                .andExpect(jsonPath("$.customer.email").value("meera@example.com"))
                .andExpect(jsonPath("$.customer.name").value("Meera Iyer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        postJson("/api/v1/auth/google", "{\"credential\":\"google:sub-1:meera@example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCustomer").value(false))
                .andExpect(jsonPath("$.customer.id").value((Integer) JsonPath.read(created, "$.customer.id")));

        postJson("/api/v1/auth/google", "{\"credential\":\"forged\"}").andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/google", "{\"credential\":\"google:sub-2:other@example.com\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileNeedsACustomerToken() throws Exception {
        mockMvc.perform(get("/api/v1/customer/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/customer/me").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }
}

package com.vaabhi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.shared.storage.ObjectStorage;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Stock, coupons, rewards, customer messages, the gallery, the activity log and notifications (phase 6). */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, ExtrasIT.FakeStorage.class})
class ExtrasIT {

    static class MemoryStorage implements ObjectStorage {
        final Set<String> objects = ConcurrentHashMap.newKeySet();

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public URI presignPut(String key, Duration validFor) {
            return URI.create("https://r2.test/" + key);
        }

        @Override
        public boolean exists(String key) {
            return objects.contains(key);
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeStorage {
        @Bean
        @Primary
        MemoryStorage extrasMemoryStorage() {
            return new MemoryStorage();
        }
    }

    /** The demo owner is staff:1. */
    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Demo Owner"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    private static final RequestPostProcessor COUNTER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Counter"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("PERM_ORDERS"));

    private static final AtomicInteger UNIQUE = new AtomicInteger(3_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemoryStorage storage;

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(
                request.with(OWNER).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private long client(String name) throws Exception {
        return id(send(post("/api/v1/admin/clients"), """
                        {"phone":"98763%05d","firstName":"%s","gender":"FEMALE","clientType":"RETAIL",
                         "whatsapp":"98763%05d"}
                        """.formatted(UNIQUE.incrementAndGet(), name, UNIQUE.get()))
                .andExpect(status().isCreated()));
    }

    private long order(long clientId, BigDecimal priceRupees, String coupon) throws Exception {
        var today = LocalDate.now();
        return id(send(post("/api/v1/admin/orders"), """
                        {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                          {"description":"Blouse","quantity":1,"priceRupees":%d}],
                         "discountKind":"AMOUNT","discountValue":0,"couponCode":%s,"advanceRupees":0,
                         "advanceMode":"CASH"}
                        """.formatted(
                                clientId,
                                today,
                                today.plusDays(4),
                                priceRupees,
                                coupon == null ? "null" : "\"" + coupon + "\""))
                .andExpect(status().isCreated()));
    }

    // --- stock ------------------------------------------------------------------------------------------------------

    @Test
    void stockMovesKeepABalanceAndWarnWhenLow() throws Exception {
        var name = "Silk lining " + UNIQUE.incrementAndGet();
        long item = id(send(post("/api/v1/admin/stock"), """
                        {"name":"%s","sku":null,"category":"Lining","unit":"M","reorderLevel":5,"costRupees":12000}
                        """.formatted(name))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onHand").value(0))
                .andExpect(jsonPath("$.low").value(true)));
        send(
                        post("/api/v1/admin/stock/" + item + "/movements"),
                        "{\"kind\":\"IN\",\"quantity\":20,\"note\":\"Bought\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(20));
        send(post("/api/v1/admin/stock/" + item + "/movements"), "{\"kind\":\"OUT\",\"quantity\":16.5}")
                .andExpect(jsonPath("$.quantity").value(-16.5))
                .andExpect(jsonPath("$.balance").value(3.5));
        send(post("/api/v1/admin/stock/" + item + "/movements"), "{\"kind\":\"OUT\",\"quantity\":10}")
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/stock/" + item).with(OWNER))
                .andExpect(jsonPath("$.onHand").value(3.5))
                .andExpect(jsonPath("$.low").value(true))
                .andExpect(jsonPath("$.valueRupees").value(42000));
        mockMvc.perform(get("/api/v1/admin/stock").param("low", "true").with(OWNER))
                .andExpect(jsonPath("$[?(@.id == " + item + ")]").exists());
        mockMvc.perform(get("/api/v1/admin/stock/" + item + "/movements").with(OWNER))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].balance").value(3.5));
        send(post("/api/v1/admin/stock"), """
                        {"name":"%s","sku":null,"category":null,"unit":"M","reorderLevel":0,"costRupees":0}
                        """.formatted(name.toUpperCase())).andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/admin/stock").with(COUNTER)).andExpect(status().isForbidden());
    }

    // --- coupons and rewards --------------------------------------------------------------------------------------

    @Test
    void couponsCheckLimitsAndCountUses() throws Exception {
        var code = "FLOW" + UNIQUE.incrementAndGet();
        send(post("/api/v1/admin/engage/coupons"), """
                        {"code":"%s","description":"Ten off","kind":"PERCENT","value":1000,"minOrderRupees":50000,
                         "validFrom":null,"validTo":null,"maxUses":1,"active":true}
                        """.formatted(code.toLowerCase()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code));
        // Counter staff (ORDERS) may check codes but not manage coupons.
        mockMvc.perform(get("/api/v1/admin/engage/coupons/check")
                        .param("code", code)
                        .param("amountRupees", "80000")
                        .with(COUNTER))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.discountRupees").value(8000));
        mockMvc.perform(get("/api/v1/admin/engage/coupons").with(COUNTER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/engage/coupons/check")
                        .param("code", code)
                        .param("amountRupees", "10000")
                        .with(OWNER))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("Needs an order of at least ₹500"));

        order(client("Coupon"), 80000, code.toLowerCase());
        mockMvc.perform(get("/api/v1/admin/engage/coupons/check")
                        .param("code", code)
                        .param("amountRupees", "80000")
                        .with(OWNER))
                .andExpect(jsonPath("$.reason").value("This coupon has been used up"));
        mockMvc.perform(get("/api/v1/admin/engage/coupons").with(OWNER))
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].uses").value(1));
        mockMvc.perform(get("/api/v1/admin/engage/coupons/check")
                        .param("code", "NOPE")
                        .param("amountRupees", "1")
                        .with(OWNER))
                .andExpect(jsonPath("$.reason").value("No coupon with this code"));
    }

    @Test
    void deliveredOrdersEarnPointsOnceAndPointsCanBeRedeemed() throws Exception {
        try {
            send(put("/api/v1/admin/engage/rewards/settings"), """
                            {"enabled":true,"pointsPer100":2,"rupeesPerPoint":50}
                            """)
                    .andExpect(jsonPath("$.enabled").value(true));
            long clientId = client("Points");
            long orderId = order(clientId, 125_000, null);
            send(post("/api/v1/admin/orders/" + orderId + "/deliver"), "").andExpect(status().isOk());
            // ₹1,250 → 12 whole ₹100s × 2 = 24 points, worth 24 × ₹0.50.
            mockMvc.perform(get("/api/v1/admin/engage/rewards/clients/" + clientId)
                            .with(COUNTER))
                    .andExpect(jsonPath("$.balance").value(24))
                    .andExpect(jsonPath("$.valueRupees").value(1200))
                    .andExpect(jsonPath("$.entries[0].kind").value("EARNED"));
            send(
                            post("/api/v1/admin/engage/rewards/clients/" + clientId),
                            "{\"kind\":\"REDEEMED\",\"points\":30,\"note\":null}")
                    .andExpect(status().isBadRequest());
            send(
                            post("/api/v1/admin/engage/rewards/clients/" + clientId),
                            "{\"kind\":\"REDEEMED\",\"points\":20,\"note\":\"Next order\"}")
                    .andExpect(jsonPath("$.balance").value(4));
            send(
                            post("/api/v1/admin/engage/rewards/clients/" + clientId),
                            "{\"kind\":\"EARNED\",\"points\":100,\"note\":null}")
                    .andExpect(status().isBadRequest());
        } finally {
            send(put("/api/v1/admin/engage/rewards/settings"), """
                            {"enabled":false,"pointsPer100":1,"rupeesPerPoint":100}
                            """).andExpect(status().isOk());
        }
    }

    // --- messages ---------------------------------------------------------------------------------------------------

    @Test
    void messagesFillTheTemplatesAndBecomeWhatsAppLinks() throws Exception {
        long clientId = client("Neha");
        long orderId = order(clientId, 60_000, null);
        var message = mockMvc.perform(post("/api/v1/admin/engage/messages")
                        .with(COUNTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"ORDER_READY\",\"orderId\":" + orderId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LINK"))
                .andExpect(jsonPath("$.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.createdBy").value("Counter"));
        String text = JsonPath.read(body(message), "$.body");
        assertThat(text).contains("Hi Neha").contains("is ready").contains("₹600");
        String link = JsonPath.read(body(message), "$.link");
        assertThat(link).startsWith("https://wa.me/9198763").contains("text=Hi%20Neha");

        send(post("/api/v1/admin/engage/messages"), "{\"purpose\":\"ORDER_READY\",\"clientId\":" + clientId + "}")
                .andExpect(status().isBadRequest());
        send(
                        post("/api/v1/admin/engage/messages"),
                        "{\"purpose\":\"CUSTOM\",\"clientId\":%d,\"body\":\"See you Monday\",\"channel\":\"SMS\"}"
                                .formatted(clientId))
                .andExpect(jsonPath("$.link").value(org.hamcrest.Matchers.startsWith("sms:+9198763")));
        send(post("/api/v1/admin/engage/messages"), "{\"purpose\":\"REWARD\",\"clientId\":" + clientId + "}")
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/engage/messages")
                        .param("clientId", Long.toString(clientId))
                        .with(OWNER))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].orderNumber").isNotEmpty());
    }

    // --- gallery ----------------------------------------------------------------------------------------------------

    @Test
    void galleryPhotosNeedTheirUploadFirst() throws Exception {
        String key = JsonPath.read(
                body(send(post("/api/v1/admin/gallery/uploads"), "").andExpect(status().isOk())), "$.key");
        var form = """
                {"kind":"DESIGN","title":"Boat neck %d","tags":"boat neck, silk","templateId":null,"clientId":null,
                 "orderId":null,"photoKey":"%s"}
                """.formatted(UNIQUE.incrementAndGet(), key);
        send(post("/api/v1/admin/gallery"), form).andExpect(status().isBadRequest());
        storage.objects.add(key + "/800.webp");
        long photo = id(send(post("/api/v1/admin/gallery"), form).andExpect(status().isCreated()));
        mockMvc.perform(get("/api/v1/admin/gallery")
                        .param("kind", "DESIGN")
                        .param("q", "SILK")
                        .with(OWNER))
                .andExpect(jsonPath("$[?(@.id == " + photo + ")]").exists());
        mockMvc.perform(delete("/api/v1/admin/gallery/" + photo).with(OWNER)).andExpect(status().isNoContent());
        assertThat(storage.objects).doesNotContain(key + "/800.webp");
    }

    // --- activity and notifications ---------------------------------------------------------------------------------

    @Test
    void ordersAndSettingsAreLoggedAndTheFeedCountsUnread() throws Exception {
        send(post("/api/v1/admin/me/notifications/seen"), "").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/me/notifications").with(COUNTER))
                .andExpect(jsonPath("$.unread").value(0));

        long clientId = client("Feed");
        long orderId = order(clientId, 40_000, null);
        send(post("/api/v1/admin/orders/" + orderId + "/payments"), """
                        {"amountRupees":10000,"mode":"UPI","refund":false,"note":null}
                        """).andExpect(status().isCreated());

        var feed = body(mockMvc.perform(get("/api/v1/admin/me/notifications").with(COUNTER)));
        assertThat(JsonPath.<Integer>read(feed, "$.unread")).isGreaterThanOrEqualTo(2);
        List<String> titles = JsonPath.read(feed, "$.recent[*].title");
        assertThat(titles).anyMatch(t -> t.contains("booked for Feed")).anyMatch(t -> t.contains("₹100 received"));

        mockMvc.perform(get("/api/v1/admin/activity")
                        .param("refType", "ORDER")
                        .param("refId", Long.toString(orderId))
                        .with(OWNER))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].kind").value("PAYMENT_RECEIVED"))
                .andExpect(jsonPath("$.items[1].actor").value("Demo Owner"));
        mockMvc.perform(get("/api/v1/admin/activity").with(COUNTER)).andExpect(status().isForbidden());

        send(put("/api/v1/admin/settings/watermark"), """
                        {"enabled":false,"text":null,"position":"BOTTOM_RIGHT","opacityPercent":40}
                        """).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/activity")
                        .param("kind", "SETTINGS_CHANGED")
                        .with(OWNER))
                .andExpect(jsonPath("$.items[0].refType").value("SETTINGS"));
        send(post("/api/v1/admin/me/notifications/seen"), "").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/me/notifications").with(COUNTER))
                .andExpect(jsonPath("$.unread").value(0));
    }
}

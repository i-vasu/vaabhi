package com.vaabhi.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shop settings against real Postgres: defaults, saving, validation, numbering and the managed lists. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class SettingsIT {

    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Asha Rao"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private NumberingService numbering;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private SettingsService settings;

    private ResultActions put(String path, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/admin/settings" + path)
                .with(OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void settingsNeedAnAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/billing")).andExpect(status().isUnauthorized());
    }

    @Test
    void savesAndReadsBackBillingAndAnnouncesTheChange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/billing").with(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeGst").value(false))
                .andExpect(jsonPath("$.defaultGstRate").value(5));

        put("/billing", """
                        {"chargeGst":true,"gstin":"27ABCDE1234F1Z5","defaultGstRate":12,"pricesIncludeTax":false,
                         "showHsnCodes":true,"discountAppliesTo":"ITEMS_BEFORE_TAX","autoInvoice":false}
                        """).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/settings/billing").with(OWNER))
                .andExpect(jsonPath("$.gstin").value("27ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.defaultGstRate").value(12))
                .andExpect(jsonPath("$.discountAppliesTo").value("ITEMS_BEFORE_TAX"));
        assertThat(settings.billing().gstApplies()).isTrue();
        assertThat(events.stream(SettingsChanged.class))
                .contains(new SettingsChanged("billing", "Updated billing and tax", "Asha Rao"));
    }

    @Test
    void rejectsInvalidValuesFieldByField() throws Exception {
        put("/billing", """
                        {"chargeGst":true,"gstin":"not-a-gstin","defaultGstRate":40,"pricesIncludeTax":true,
                         "showHsnCodes":false,"discountAppliesTo":"ORDER_TOTAL","autoInvoice":true}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'gstin')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'defaultGstRate')]").exists());
        put("/printing", """
                        {"formats":["A4"],"defaultFormat":"THERMAL_58MM","showLogo":true,"showMeasurements":true,
                         "showClothPhotos":false,"showBankDetails":true,"showUpiQr":true,"showTerms":true,
                         "showSignature":true,"showStaffName":false}
                        """).andExpect(status().isBadRequest());
    }

    @Test
    void shopProfileKeepsAddressAndHours() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/shop-profile").with(OWNER))
                .andExpect(jsonPath("$.shopName").value("vaabhi"))
                .andExpect(jsonPath("$.opensAt").value("09:00:00"));

        put("/shop-profile", """
                        {"ownerName":"Asha Rao","shopName":"Vaabhi Boutique","businessType":"LADIES_BOUTIQUE",
                         "phone":"+91 70134 68353","publicEmail":"shop@vaabhi.com","googleReviewLink":null,
                         "website":"https://vaabhi.com","mapsLink":null,"instagram":"https://instagram.com/vaabhi",
                         "facebook":null,"youtube":null,"threads":null,"address":"Andhra Pradesh, India","doorNo":"12",
                         "street":"MG Road","area":null,"city":"Vijayawada","district":"NTR","state":"Andhra Pradesh",
                         "country":"IN","pinCode":"520001","currency":"INR","opensAt":"10:00","closesAt":"20:30",
                         "logoKey":null}
                        """).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/settings/shop-profile").with(OWNER))
                .andExpect(jsonPath("$.shopName").value("Vaabhi Boutique"))
                .andExpect(jsonPath("$.closesAt").value("20:30:00"))
                .andExpect(jsonPath("$.pinCode").value("520001"));

        // A logo key that was never uploaded is refused.
        put("/shop-profile", """
                        {"ownerName":"Asha Rao","shopName":"Vaabhi Boutique","businessType":"LADIES_BOUTIQUE",
                         "country":"IN","currency":"INR","opensAt":"10:00","closesAt":"20:30",
                         "logoKey":"settings/logo/../../products/1"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Upload the image again"));
    }

    @Test
    void calendarDocumentsAndTheOtherSections() throws Exception {
        put("/calendar", """
                        {"weeklyOff":["SUNDAY"],"holidays":[{"date":"2026-11-08","name":"Diwali"}]}
                        """).andExpect(status().isOk());
        assertThat(settings.calendar().isClosed(java.time.LocalDate.of(2026, 11, 8)))
                .isTrue();
        assertThat(settings.calendar().isClosed(java.time.LocalDate.of(2026, 11, 9)))
                .isFalse();
        assertThat(settings.calendar().isClosed(java.time.LocalDate.of(2026, 11, 15)))
                .isTrue();

        put("/documents", """
                        {"slogan":"Tailored with love","bankName":"State Bank of India","branch":"Benz Circle",
                         "accountNumber":"12345678901","ifsc":"SBIN0001234","upiId":"vaabhi@oksbi","upiQrKey":null,
                         "alterationUndertaking":null,"terms":"Goods once sold are not taken back."}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ifsc").value("SBIN0001234"));
        assertThat(settings.documents().undertaking()).isEqualTo(DocumentSettings.DEFAULT_UNDERTAKING);

        put("/orders", """
                        {"trialDate":true,"subCategories":false,"barcodes":true,"lazyProductList":false,
                         "tailorChargeOnAddons":false}
                        """).andExpect(status().isOk());
        put("/notifications", """
                        {"newOrder":true,"orderReady":false,"orderDelivered":true,"paymentReceived":true,
                         "dataExported":true,"morningSchedule":false,"dailySummary":true}
                        """).andExpect(status().isOk());
        put("/watermark", """
                        {"enabled":true,"text":"VAABHI","position":"CENTER","opacityPercent":40}
                        """).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/settings/messages").with(OWNER))
                .andExpect(jsonPath("$.channel").value("WHATSAPP"));
        mockMvc.perform(get("/api/v1/admin/settings/printing").with(OWNER))
                .andExpect(jsonPath("$.defaultFormat").value("A4"));
        for (var section : List.of("orders", "notifications", "watermark", "calendar", "documents")) {
            mockMvc.perform(get("/api/v1/admin/settings/" + section).with(OWNER))
                    .andExpect(status().isOk());
        }
        assertThat(settings.orders().trialDate()).isTrue();
        assertThat(settings.notifications().orderReady()).isFalse();
        assertThat(settings.watermark().opacityPercent()).isEqualTo(40);
    }

    @Test
    void numberingPreviewsAndHandsOutNumbers() throws Exception {
        put("/numbering", """
                        [{"kind":"ORDER","prefix":"ORD-","nextValue":2042,"suffix":"","pad":0,"preview":""},
                         {"kind":"ITEM","prefix":"IT","nextValue":7,"suffix":"/26","pad":3,"preview":""}]
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind == 'ORDER')].preview").value("ORD-2042"))
                .andExpect(jsonPath("$[?(@.kind == 'ITEM')].preview").value("IT007/26"));

        var first = transactions.execute(tx -> numbering.next(NumberSequence.Kind.ORDER));
        var second = transactions.execute(tx -> numbering.next(NumberSequence.Kind.ORDER));
        assertThat(List.of(first, second)).containsExactly("ORD-2042", "ORD-2043");

        put("/numbering", """
                        [{"kind":"INVOICE","prefix":"INV 1","nextValue":1,"suffix":"","pad":0,"preview":""}]
                        """).andExpect(status().isBadRequest());
    }

    @Test
    void stagesKeepReadyAndDeliveredLast() throws Exception {
        var stages = mockMvc.perform(get("/api/v1/admin/settings/stages").with(OWNER))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].name").value("Cutting"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> ids = JsonPath.read(stages, "$[*].id");

        // Rename Stitching, drop Finishing, add Embroidery.
        put("/stages", """
                        [{"id":%d,"name":"Cutting","kind":"WORK"},
                         {"id":%d,"name":"Stitching & lining","kind":"WORK"},
                         {"id":null,"name":"Embroidery","kind":"WORK"},
                         {"id":%d,"name":"Ready for pickup","kind":"READY"},
                         {"id":%d,"name":"Delivered","kind":"DELIVERED"}]
                        """.formatted(ids.get(0), ids.get(1), ids.get(3), ids.get(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name")
                        .value(org.hamcrest.Matchers.contains(
                                "Cutting", "Stitching & lining", "Embroidery", "Ready for pickup", "Delivered")));

        put("/stages", """
                        [{"id":%d,"name":"Ready for pickup","kind":"READY"},
                         {"id":%d,"name":"Cutting","kind":"WORK"},
                         {"id":%d,"name":"Delivered","kind":"DELIVERED"}]
                        """.formatted(ids.get(3), ids.get(0), ids.get(4))).andExpect(status().isBadRequest());
        put("/stages", """
                        [{"id":%d,"name":"Cutting","kind":"WORK"},
                         {"id":%d,"name":"cutting","kind":"WORK"},
                         {"id":%d,"name":"Ready","kind":"READY"},
                         {"id":%d,"name":"Delivered","kind":"DELIVERED"}]
                        """.formatted(ids.get(0), ids.get(1), ids.get(3), ids.get(4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("cutting appears twice"));
        put("/stages", """
                        [{"id":%d,"name":"Cutting","kind":"WORK"},
                         {"id":%d,"name":"Ready","kind":"READY"},
                         {"id":%d,"name":"Delivered","kind":"DELIVERED"}]
                        """.formatted(ids.get(0), ids.get(1), ids.get(4))).andExpect(status().isBadRequest());
        put("/stages", """
                        [{"id":%d,"name":"Cutting","kind":"WORK"},
                         {"id":null,"name":"Ready again","kind":"READY"},
                         {"id":%d,"name":"Delivered","kind":"DELIVERED"}]
                        """.formatted(ids.get(0), ids.get(4))).andExpect(status().isBadRequest());
    }

    @Test
    void deliveryPackagesAreReplacedAsAList() throws Exception {
        var saved = put("/delivery-packages", """
                        [{"id":null,"name":"Standard","priceRupees":0,"days":7},
                         {"id":null,"name":"Express","priceRupees":20000,"days":3}]
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        int express = JsonPath.read(saved, "$[1].id");

        put("/delivery-packages", """
                        [{"id":%d,"name":"Express","priceRupees":25000,"days":2}]
                        """.formatted(express))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].priceRupees").value(25000));
        mockMvc.perform(get("/api/v1/admin/settings/delivery-packages").with(OWNER))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void garmentTemplatesCanBeAddedEditedAndRemoved() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/garment-templates").with(OWNER))
                .andExpect(jsonPath("$[0].name").value("Blouse"))
                .andExpect(jsonPath("$[0].fields[1].key").value("bust"));

        var body = """
                {"id":null,"name":"Saree fall","businessType":"LADIES",
                 "fields":[{"key":"length","label":"Length","unit":"in"},{"key":"style","label":"Style","unit":""}]}
                """;
        var created = mockMvc.perform(post("/api/v1/admin/settings/garment-templates")
                        .with(OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int id = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/v1/admin/settings/garment-templates")
                        .with(OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        put("/garment-templates/" + id, body.replace("Saree fall", "Saree pico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Saree pico"));
        put("/garment-templates/" + id, body.replace("\"style\"", "\"length\"")).andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/admin/settings/garment-templates/" + id).with(OWNER))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/settings/garment-templates/" + id).with(OWNER))
                .andExpect(status().isNotFound());
        // The same name can be added again after removal.
        mockMvc.perform(post("/api/v1/admin/settings/garment-templates")
                        .with(OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Saree fall", "Saree pico")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
        put("/garment-templates/999999", body.replace("Saree fall", "Nothing")).andExpect(status().isNotFound());
    }

    @Test
    void imageUploadNeedsStorage() throws Exception {
        mockMvc.perform(post("/api/v1/admin/settings/images")
                        .with(OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"LOGO\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}

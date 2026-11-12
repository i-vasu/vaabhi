package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Tax classes and the slab rule against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class TaxClassServiceIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static final String BILLING = """
            {"chargeGst":false,"gstin":null,"defaultGstRate":5,"pricesIncludeTax":true,"showHsnCodes":false,
             "discountAppliesTo":"ORDER_TOTAL","autoInvoice":true,"defaultTaxClassId":%s}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TaxClassService taxClasses;

    @BeforeEach
    void defaultGarments() throws Exception {
        saveBilling(garmentsId());
    }

    private void saveBilling(Long defaultClassId) throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings/billing")
                        .with(jwt().jwt(j -> j.subject("staff:1").claim("name", "Asha Rao"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                                        new SimpleGrantedAuthority("ROLE_OWNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BILLING.formatted(defaultClassId)))
                .andExpect(status().isOk());
    }

    private long garmentsId() {
        return jdbc.sql("SELECT id FROM tax_class WHERE name = 'Garments'")
                .query(Long.class)
                .single();
    }

    private static TaxClassForm form(String name, BigDecimal slabLimit, BigDecimal above) {
        return new TaxClassForm(
                name + " " + SEQUENCE.incrementAndGet(),
                TaxClass.Kind.GOODS,
                "6204",
                new BigDecimal("5"),
                slabLimit,
                above);
    }

    @Test
    void resolveUsesLowerRateAtLimit() {
        var rate = taxClasses.resolve(garmentsId(), new BigDecimal("2500.00"), false);

        assertThat(rate).isNotNull();
        assertThat(rate.ratePercent()).isEqualByComparingTo("5");
        assertThat(rate.hsnCode()).isEqualTo("6204");
    }

    @Test
    void resolveUsesHigherRateAboveLimit() {
        var rate = taxClasses.resolve(garmentsId(), new BigDecimal("2500.01"), false);

        assertThat(rate).isNotNull();
        assertThat(rate.ratePercent()).isEqualByComparingTo("18");
    }

    @Test
    void resolveFallsBackToDefault() {
        var rate = taxClasses.resolve(null, new BigDecimal("100.00"), false);

        assertThat(rate).isNotNull();
        assertThat(rate.hsnCode()).isEqualTo("6204");
        assertThat(rate.kind()).isEqualTo(TaxClass.Kind.GOODS);
    }

    @Test
    void resolveWithoutDefaultIsNull() throws Exception {
        saveBilling(null);

        assertThat(taxClasses.resolve(null, new BigDecimal("100.00"), false)).isNull();
    }

    @Test
    void inclusiveSlabUsesValueBeforeTax() {
        var atLimit = taxClasses.resolve(garmentsId(), new BigDecimal("2625.00"), true);
        var above = taxClasses.resolve(garmentsId(), new BigDecimal("2625.27"), true);

        assertThat(atLimit).isNotNull();
        assertThat(atLimit.ratePercent()).isEqualByComparingTo("5");
        assertThat(above).isNotNull();
        assertThat(above.ratePercent()).isEqualByComparingTo("18");
    }

    @Test
    void deleteInUseRefused() {
        var created = taxClasses.create(form("In use", null, null));
        jdbc.sql("UPDATE garment_template SET tax_class_id = :id WHERE id = (SELECT min(id) FROM garment_template)")
                .param("id", created.id())
                .update();
        try {
            assertThat(taxClasses.get(created.id()).inUse()).isTrue();
            assertThatThrownBy(() -> taxClasses.delete(created.id()))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        } finally {
            jdbc.sql("UPDATE garment_template SET tax_class_id = NULL WHERE tax_class_id = :id")
                    .param("id", created.id())
                    .update();
        }
        taxClasses.delete(created.id());
        assertThat(taxClasses.list()).noneMatch(c -> c.id() == created.id());
    }

    @Test
    void slabNeedsBothFields() {
        assertThatThrownBy(() -> taxClasses.create(form("Half slab", new BigDecimal("1000"), null)))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> taxClasses.create(form("Half slab", null, new BigDecimal("12"))))
                .isInstanceOf(ResponseStatusException.class);
    }
}

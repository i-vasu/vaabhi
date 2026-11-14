package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** TDS sections, challans and the month summary, against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class TdsServiceIT extends BooksFixtures {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final RequestPostProcessor OWNER =
            jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    @Autowired
    private TdsService tds;

    @Autowired
    private MockMvc mockMvc;

    private static LocalDate today() {
        return LocalDate.now(IST);
    }

    private static TdsSectionForm sectionForm(String code) {
        return new TdsSectionForm(code, "Test section", new BigDecimal("1"), new BigDecimal("20"), null, null, true);
    }

    private long supplier() {
        return jdbc.sql("INSERT INTO party (name, is_supplier) VALUES (:name, TRUE) RETURNING id")
                .param("name", unique("TDS supplier "))
                .query(Long.class)
                .single();
    }

    private void bill(long party, long section, BigDecimal total, BigDecimal tds) {
        jdbc.sql("""
                        INSERT INTO supplier_bill (party_id, bill_no, bill_date, taxable_rupees, tax_rupees,
                                                   total_rupees, tds_section_id, tds_rupees, payable_rupees)
                        VALUES (:party, :number, :date, :total, 0, :total, :section, :tds, :payable)
                        """)
                .param("party", party)
                .param("number", unique("TDS-BILL-"))
                .param("date", today())
                .param("total", total)
                .param("section", section)
                .param("tds", tds)
                .param("payable", total.subtract(tds))
                .update();
    }

    @Test
    void sectionsSeededAndEditable() {
        assertThat(tds.sections())
                .extracting(TdsSectionView::code)
                .contains("194C-I", "194C-O", "194I-B", "194I-A", "194J", "194H");

        var created = tds.createSection(sectionForm(unique("TS")));
        var edited = tds.updateSection(
                created.id(),
                new TdsSectionForm(
                        created.code(),
                        "Edited section",
                        new BigDecimal("2"),
                        new BigDecimal("20"),
                        new BigDecimal("50000"),
                        null,
                        false));
        assertThat(edited.name()).isEqualTo("Edited section");
        assertThat(edited.ratePercent()).isEqualByComparingTo("2");
        assertThat(edited.yearThresholdRupees()).isEqualByComparingTo("50000");
        assertThat(edited.active()).isFalse();

        assertThatThrownBy(() -> tds.createSection(sectionForm(created.code())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A section with this code exists");
    }

    @Test
    void summaryDeductedVsPaid() {
        var section = tds.createSection(sectionForm(unique("TD")));
        long party = supplier();
        bill(party, section.id(), new BigDecimal("1000"), new BigDecimal("100"));
        bill(party, section.id(), new BigDecimal("500"), new BigDecimal("50"));
        var month = YearMonth.from(today());
        tds.createChallan(
                new TdsChallanForm(
                        section.id(),
                        month.toString(),
                        new BigDecimal("150"),
                        BigDecimal.ZERO,
                        unique("CH-"),
                        "1234567",
                        today(),
                        "UPI"),
                null);

        var row = tds.summary(month.atDay(1), today()).stream()
                .filter(r -> r.sectionCode().equals(section.code()) && r.month().equals(month))
                .findFirst()
                .orElseThrow();

        assertThat(row.deductedRupees()).isEqualByComparingTo("150");
        assertThat(row.paidRupees()).isEqualByComparingTo("150");
        assertThat(row.balanceRupees()).isEqualByComparingTo("0");
    }

    @Test
    void challanPostsAgainstTdsPayableAndInterest() {
        long sectionId = jdbc.sql("SELECT id FROM tds_section WHERE code = '194J'")
                .query(Long.class)
                .single();
        var challanNo = unique("CH-");
        long id = tds.createChallan(
                        new TdsChallanForm(
                                sectionId,
                                YearMonth.from(today()).toString(),
                                new BigDecimal("150"),
                                new BigDecimal("10"),
                                challanNo,
                                "1234567",
                                today(),
                                "UPI"),
                        "IT")
                .id();

        sweeper.sweepNow();

        var posted = entry("TDS_CHALLAN", id, "PAYMENT");
        assertThat(posted.narration()).isEqualTo("TDS challan " + challanNo + " (194J)");
        assertThat(netInEntry(posted.id(), accountId("TDS_PAYABLE"))).isEqualByComparingTo("150");
        assertThat(netInEntry(posted.id(), accountId("EXPENSE_OTHER"))).isEqualByComparingTo("10");
        assertThat(netInEntry(posted.id(), settings.accountFor("UPI"))).isEqualByComparingTo("-160");
    }

    @Test
    void badBsrCodeIs400() throws Exception {
        long sectionId = jdbc.sql("SELECT id FROM tds_section WHERE code = '194J'")
                .query(Long.class)
                .single();
        String body = """
                {"tdsSectionId":%d,"periodMonth":"%s","amountRupees":1000.00,"interestRupees":0,
                 "challanNo":"CH-IT-1","bsrCode":"12345","paidOn":"%s","mode":"UPI"}
                """.formatted(sectionId, YearMonth.from(today()), today());
        mockMvc.perform(post("/api/v1/admin/accounts/tds/challans")
                        .with(OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

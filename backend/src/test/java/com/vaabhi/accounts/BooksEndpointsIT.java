package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaabhi.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Every books read endpoint answers the owner: lists and reports return 200, CSV downloads are text/csv, and asking
 * for a row that does not exist is a client error rather than a crash.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class BooksEndpointsIT {

    private static final RequestPostProcessor OWNER =
            jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));
    private static final long MISSING = 987_654_321L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    private static String month() {
        return YearMonth.from(today()).toString();
    }

    private static String fy() {
        return FiscalYears.label(FiscalYears.startOf(today()));
    }

    private void ok(String path) throws Exception {
        mockMvc.perform(get(path).with(OWNER)).andExpect(status().isOk());
    }

    private void csv(String path) throws Exception {
        mockMvc.perform(get(path).with(OWNER))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    private void missing(String path) throws Exception {
        var status =
                mockMvc.perform(get(path).with(OWNER)).andReturn().getResponse().getStatus();
        assertThat(status).as(path).isBetween(400, 499);
    }

    @Test
    void accountsReportsAnswer() throws Exception {
        long account = jdbc.sql("SELECT id FROM account WHERE active ORDER BY code LIMIT 1")
                .query(Long.class)
                .single();
        ok("/api/v1/admin/accounts/accounts");
        ok("/api/v1/admin/accounts/accounts/" + account + "/ledger");
        ok("/api/v1/admin/accounts/accounts/" + account + "/ledger?from=" + today().withDayOfMonth(1) + "&to="
                + today());
        ok("/api/v1/admin/accounts/journal?q=sale&page=1&size=10");
        ok("/api/v1/admin/accounts/trial-balance");
        ok("/api/v1/admin/accounts/opening-balances");
        ok("/api/v1/admin/accounts/periods");
        ok("/api/v1/admin/accounts/status");
        ok("/api/v1/admin/accounts/problems");
        ok("/api/v1/admin/accounts/settings");
        missing("/api/v1/admin/accounts/journal/" + MISSING);
    }

    @Test
    void gstReportsAnswerAsJsonAndCsv() throws Exception {
        var from = today().withDayOfMonth(1);
        mockMvc.perform(get("/api/v1/admin/accounts/gstr3b")
                        .param("month", month())
                        .with(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(month()));
        csv("/api/v1/admin/accounts/gstr3b.csv?month=" + month());
        ok("/api/v1/admin/accounts/input-gst?from=" + from + "&to=" + today());
        csv("/api/v1/admin/accounts/input-gst.csv?from=" + from + "&to=" + today());
        ok("/api/v1/admin/money/gstr1?month=" + month());
        for (var section : new String[] {"b2b", "b2cl", "b2cs", "cdnr", "cdnur", "hsn", "docs"}) {
            csv("/api/v1/admin/money/gstr1/" + section + ".csv?month=" + month());
        }
        missing("/api/v1/admin/money/gstr1/nope.csv?month=" + month());
        missing("/api/v1/admin/accounts/gstr3b?month=not-a-month");
    }

    @Test
    void tdsAnswers() throws Exception {
        var from = FiscalYears.startOf(today());
        ok("/api/v1/admin/accounts/tds/sections");
        ok("/api/v1/admin/accounts/tds/summary?from=" + from + "&to=" + today());
        ok("/api/v1/admin/accounts/tds/challans?from=" + from + "&to=" + today());
        for (int quarter = 1; quarter <= 4; quarter++) {
            ok("/api/v1/admin/accounts/tds/returns?fy=" + fy() + "&quarter=" + quarter);
        }
        csv("/api/v1/admin/accounts/tds/returns.csv?fy=" + fy() + "&quarter=1&part=deductees");
        csv("/api/v1/admin/accounts/tds/returns.csv?fy=" + fy() + "&quarter=1&part=challans");
        missing("/api/v1/admin/accounts/tds/returns.csv?fy=" + fy() + "&quarter=1&part=other");
        missing("/api/v1/admin/accounts/tds/returns?fy=2026-99&quarter=1");
    }

    @Test
    void bankAnswers() throws Exception {
        ok("/api/v1/admin/accounts/bank/statements");
        ok("/api/v1/admin/accounts/bank/razorpay/settlements");
        ok("/api/v1/admin/accounts/bank/cod-remittances");
        missing("/api/v1/admin/accounts/bank/statements/" + MISSING);
        missing("/api/v1/admin/accounts/bank/statements/" + MISSING + "?status=UNMATCHED");
        missing("/api/v1/admin/accounts/bank/statements/" + MISSING + "/report");
        missing("/api/v1/admin/accounts/bank/statements/" + MISSING + "/report.csv");
        missing("/api/v1/admin/accounts/bank/lines/" + MISSING + "/candidates");
    }

    @Test
    void assetsAndYearEndAnswer() throws Exception {
        ok("/api/v1/admin/accounts/assets/classes");
        ok("/api/v1/admin/accounts/assets");
        mockMvc.perform(get("/api/v1/admin/accounts/year-end/" + fy()).with(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fy").value(fy()));
        missing("/api/v1/admin/accounts/year-end/2026-99");
    }

    @Test
    void purchasesAndMoneyAnswer() throws Exception {
        ok("/api/v1/admin/money/purchases/suppliers");
        ok("/api/v1/admin/money/purchases/suppliers?q=silk");
        ok("/api/v1/admin/money/purchases/bills");
        ok("/api/v1/admin/money/purchases/bills?status=OPEN&month=" + month());
        ok("/api/v1/admin/money/purchases/payments");
        missing("/api/v1/admin/money/purchases/suppliers/" + MISSING);
        missing("/api/v1/admin/money/purchases/bills/" + MISSING);
        ok("/api/v1/admin/money/credit-notes");
        ok("/api/v1/admin/money/credit-notes?q=CN&page=1&size=10");
        ok("/api/v1/admin/money/tax-classes");
        missing("/api/v1/admin/money/credit-notes/" + MISSING);
        ok("/api/v1/admin/money/invoices/" + MISSING + "/credit-notes");
    }
}

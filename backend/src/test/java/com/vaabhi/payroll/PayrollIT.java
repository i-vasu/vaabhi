package com.vaabhi.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Payroll end to end: attendance, piece work from the production ledger and an advance feed a month's payslips;
 * finalising freezes them, paying records a salary expense, and the statement balances. Each test uses its own
 * month in 2001 so the shared database does not mix them up.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class PayrollIT {

    /** The demo owner is staff:1. */
    private static final RequestPostProcessor OWNER = jwt().jwt(
                    j -> j.subject("staff:1").claim("name", "Demo Owner"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OWNER"));

    private static final AtomicInteger PHONES = new AtomicInteger(9_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

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

    private long staff(String name, String payType, BigDecimal salaryRupees, BigDecimal hourlyRupees) throws Exception {
        return id(send(post("/api/v1/admin/staff"), """
                        {"name":"%s","phone":null,"email":null,"role":"STAFF","designation":"Tailor","joinedOn":null,
                         "payType":"%s","salaryRupees":%d,"hourlyRupees":%d,"shiftStart":"10:00","shiftEnd":"19:00",
                         "lateGraceMinutes":10,"weeklyOff":null,"password":null}
                        """.formatted(name, payType, salaryRupees, hourlyRupees))
                .andExpect(status().isCreated()));
    }

    private void mark(long staffId, String day, String status, String checkIn, String checkOut) throws Exception {
        send(put("/api/v1/admin/staff/attendance/" + day + "/" + staffId), """
                        {"status":"%s","checkIn":%s,"checkOut":%s,"note":null}
                        """.formatted(
                                status,
                                checkIn == null ? "null" : "\"" + checkIn + "\"",
                                checkOut == null ? "null" : "\"" + checkOut + "\""))
                .andExpect(status().isOk());
    }

    private long stage(int offset) {
        return jdbc.sql(
                        "SELECT id FROM production_stage WHERE kind = 'WORK' AND active ORDER BY position OFFSET :n LIMIT 1")
                .param("n", offset)
                .query(Long.class)
                .single();
    }

    /**
     * A tailor finishes the first work stage of 3 pieces of a price-list item whose first stage pays ₹150, dated
     * into {@code day} (the stage log is written with the current time, so the test moves it back).
     */
    private void finishFirstStage(long tailorId, LocalDate day) throws Exception {
        var product = id(send(post("/api/v1/admin/service-products"), """
                        {"templateId":%d,"name":"Payroll blouse %d","priceRupees":60000,
                         "stageCosts":[{"stageId":%d,"rupees":15000}]}
                        """.formatted(
                                jdbc.sql("SELECT id FROM garment_template WHERE name = 'Blouse'")
                                        .query(Long.class)
                                        .single(),
                                PHONES.incrementAndGet(),
                                stage(0)))
                .andExpect(status().isCreated()));
        var client = id(send(post("/api/v1/admin/clients"), """
                        {"phone":"98762%05d","firstName":"Payroll","gender":"FEMALE","clientType":"RETAIL"}
                        """.formatted(PHONES.incrementAndGet()))
                .andExpect(status().isCreated()));
        var today = LocalDate.now();
        var order = send(post("/api/v1/admin/orders"), """
                        {"clientId":%d,"orderDate":"%s","deliveryDate":"%s","items":[
                          {"serviceProductId":%d,"description":"Blouse","quantity":3,"priceRupees":60000}],
                         "discountKind":"AMOUNT","discountValue":0,"advanceRupees":0,"advanceMode":"CASH"}
                        """.formatted(client, today, today.plusDays(5), product))
                .andExpect(status().isCreated());
        long orderId = id(order);
        long itemId = ((Number) JsonPath.read(body(order), "$.items[0].id")).longValue();
        send(put("/api/v1/admin/workshop/items/" + itemId + "/assignee"), "{\"staffId\":" + tailorId + "}")
                .andExpect(status().isOk());
        send(post("/api/v1/admin/orders/" + orderId + "/stage"), "{\"stageId\":%d,\"itemIds\":[]}".formatted(stage(1)))
                .andExpect(status().isOk());
        jdbc.sql("UPDATE order_item_stage_log SET moved_at = :at WHERE item_id = :item")
                .param(
                        "at",
                        day.atTime(12, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toOffsetDateTime())
                .param("item", itemId)
                .update();
    }

    private Map<String, Object> slip(String month, long staffId) throws Exception {
        List<Map<String, Object>> slips = JsonPath.read(
                body(mockMvc.perform(
                        get("/api/v1/admin/payroll/months/" + month).with(OWNER))),
                "$.slips[?(@.staffId == " + staffId + ")]");
        assertThat(slips).hasSize(1);
        return slips.getFirst();
    }

    private static BigDecimal rupees(Map<String, Object> slip, String field) {
        return ((Number) slip.get(field)).longValue();
    }

    @Test
    void monthlyAndPieceWorkersFromPreviewToPaidAndTheStatementBalances() throws Exception {
        var month = "2001-09"; // 30 days
        long monthly = staff("Mohan Monthly", "MONTHLY", 1_500_000, 12_000);
        long piece = staff("Pooja Piece", "PIECE", 0, 0);

        mark(monthly, "2001-09-03", "ABSENT", null, null);
        mark(monthly, "2001-09-04", "HALF_DAY", null, null);
        mark(monthly, "2001-09-05", "PRESENT", "10:40", "21:00"); // 40 late, 120 overtime
        finishFirstStage(piece, LocalDate.of(2001, 9, 12));

        // An advance for the monthly worker, approved before the month is closed.
        long advance = id(send(post("/api/v1/admin/staff/requests"), """
                        {"staffId":%d,"kind":"ADVANCE","fromDate":"2001-09-10","amountRupees":200000,"reason":"Rent"}
                        """.formatted(monthly))
                .andExpect(status().isCreated()));
        send(post("/api/v1/admin/staff/requests/" + advance + "/decision"), "{\"approve\":true,\"note\":null}")
                .andExpect(status().isOk());

        var preview = slip(month, monthly);
        assertThat(preview.get("id")).isNull();
        // 1.5 unpaid days of 30: 1,500,000 × 28.5 / 30 = 1,425,000; overtime 120 min × ₹120/h = 24,000;
        // late 40 min = 8,000; advance 200,000.
        assertThat(rupees(preview, "baseRupees")).isEqualTo(1_425_000);
        assertThat(rupees(preview, "overtimeRupees")).isEqualTo(24_000);
        assertThat(rupees(preview, "lateRupees")).isEqualTo(8_000);
        assertThat(rupees(preview, "advanceRupees")).isEqualTo(200_000);
        assertThat(rupees(preview, "netRupees")).isEqualTo(1_425_000 + 24_000 - 8_000 - 200_000);
        var pieceSlip = slip(month, piece);
        assertThat(rupees(pieceSlip, "pieces")).isEqualTo(3);
        assertThat(rupees(pieceSlip, "pieceRupees")).isEqualTo(45_000);

        mockMvc.perform(get("/api/v1/admin/payroll/production")
                        .param("from", "2001-09-01")
                        .param("to", "2001-09-30")
                        .param("staffId", Long.toString(piece))
                        .with(OWNER))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].rateRupees").value(15000))
                .andExpect(jsonPath("$.byStaff[0].amountRupees").value(45000));

        send(post("/api/v1/admin/payroll/months/" + month + "/finalize"), "")
                .andExpect(jsonPath("$.finalized").value(true));
        send(post("/api/v1/admin/payroll/months/" + month + "/finalize"), "").andExpect(status().isConflict());
        var frozen = slip(month, monthly);
        long slipId = ((Number) frozen.get("id")).longValue();
        assertThat(rupees(frozen, "netRupees")).isEqualTo(1_241_000);

        // The advance is now taken back: next month starts clean.
        assertThat(rupees(slip("2001-10", monthly), "advanceRupees")).isZero();

        send(post("/api/v1/admin/payroll/payslips/" + slipId + "/pay"), "{\"mode\":\"BANK\"}")
                .andExpect(jsonPath("$.paidMode").value("BANK"))
                .andExpect(jsonPath("$.paidAt").exists());
        send(post("/api/v1/admin/payroll/payslips/" + slipId + "/pay"), "{\"mode\":\"BANK\"}")
                .andExpect(status().isConflict());
        var expense = jdbc.sql("SELECT category, amount_rupees, paid_to FROM expense WHERE id ="
                        + " (SELECT expense_id FROM payslip WHERE id = :id)")
                .param("id", slipId)
                .query((rs, n) -> List.of(rs.getString(1), rs.getLong(2), rs.getString(3)))
                .single();
        assertThat(expense).containsExactly("SALARY", 1_241_000L, "Mohan Monthly");
        send(post("/api/v1/admin/payroll/months/" + month + "/reopen"), "").andExpect(status().isConflict());

        // Earned 1,441,000 − advance 200,000 − paid 1,241,000 = nothing owed either way.
        mockMvc.perform(get("/api/v1/admin/payroll/staff/" + monthly + "/statement")
                        .with(OWNER))
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].kind").value("ADVANCE"))
                .andExpect(jsonPath("$.entries[1].kind").value("EARNED"))
                .andExpect(jsonPath("$.entries[1].amountRupees").value(1441000))
                .andExpect(jsonPath("$.balanceRupees").value(0));
    }

    @Test
    void anUnpaidMonthCanBeReopenedAndItsAdvancesComeBack() throws Exception {
        var month = "2001-11";
        long daily = staff("Divya Daily", "DAILY", 60_000, 0);
        mark(daily, "2001-11-05", "PRESENT", null, null);
        mark(daily, "2001-11-06", "PRESENT", null, null);
        long advance = id(
                send(post("/api/v1/admin/staff/requests"), """
                        {"staffId":%d,"kind":"ADVANCE","fromDate":"2001-11-02","amountRupees":50000,"reason":null}
                        """.formatted(daily)).andExpect(status().isCreated()));
        send(post("/api/v1/admin/staff/requests/" + advance + "/decision"), "{\"approve\":true,\"note\":null}")
                .andExpect(status().isOk());

        send(post("/api/v1/admin/payroll/months/" + month + "/finalize"), "").andExpect(status().isOk());
        assertThat(rupees(slip(month, daily), "netRupees")).isEqualTo(120_000 - 50_000);
        send(post("/api/v1/admin/payroll/months/" + month + "/reopen"), "")
                .andExpect(jsonPath("$.finalized").value(false));
        assertThat(slip(month, daily).get("id")).isNull();
        assertThat(rupees(slip(month, daily), "advanceRupees")).isEqualTo(50_000);
    }

    @Test
    void monthsStillToComeCannotBeClosed() throws Exception {
        var next = YearMonth.now().plusMonths(1).toString();
        send(post("/api/v1/admin/payroll/months/" + next + "/finalize"), "").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/payroll/months/not-a-month").with(OWNER))
                .andExpect(status().isBadRequest());
    }
}

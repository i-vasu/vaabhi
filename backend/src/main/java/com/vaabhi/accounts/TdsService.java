package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** TDS sections, the challans that pay them and the month-by-month amount still owed. */
@Service
public class TdsService {

    private static final List<String> MODES = List.of("CASH", "UPI", "CARD", "BANK");
    private static final String SECTION_SELECT = """
            SELECT id, code, name, rate_percent, no_pan_rate_percent, year_threshold_rupees,
                   single_threshold_rupees, active
            FROM tds_section
            """;
    private static final String CHALLAN_SELECT = """
            SELECT c.id, c.tds_section_id, s.code AS section_code, c.period_month, c.amount_rupees,
                   c.interest_rupees, c.challan_no, c.bsr_code, c.paid_on, c.mode,
                   EXISTS (SELECT 1 FROM journal_entry j
                           WHERE j.source_type = 'TDS_CHALLAN' AND j.source_step = 'PAYMENT'
                             AND j.source_id = c.id) AS posted
            FROM tds_challan c
            JOIN tds_section s ON s.id = c.tds_section_id
            """;

    private final JdbcClient jdbc;

    TdsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record ChallanRow(
            long id,
            long tdsSectionId,
            String sectionCode,
            LocalDate periodMonth,
            BigDecimal amountRupees,
            BigDecimal interestRupees,
            String challanNo,
            String bsrCode,
            LocalDate paidOn,
            String mode,
            boolean posted) {

        TdsChallanView toView() {
            return new TdsChallanView(
                    id,
                    tdsSectionId,
                    sectionCode,
                    YearMonth.from(periodMonth),
                    amountRupees,
                    interestRupees,
                    challanNo,
                    bsrCode,
                    paidOn,
                    mode,
                    posted);
        }
    }

    private record SummaryRow(
            String sectionCode, String sectionName, LocalDate month, BigDecimal deductedRupees, BigDecimal paidRupees) {

        TdsMonthSummary toSummary() {
            return new TdsMonthSummary(
                    sectionCode,
                    sectionName,
                    YearMonth.from(month),
                    deductedRupees,
                    paidRupees,
                    Rupees.of(deductedRupees.subtract(paidRupees)));
        }
    }

    @Transactional(readOnly = true)
    public List<TdsSectionView> sections() {
        return jdbc.sql(SECTION_SELECT + " ORDER BY code")
                .query(TdsSectionView.class)
                .list();
    }

    @Transactional
    public TdsSectionView createSection(TdsSectionForm form) {
        checkCodeFree(form.code().trim(), -1);
        long id = jdbc.sql("""
                        INSERT INTO tds_section (code, name, rate_percent, no_pan_rate_percent, year_threshold_rupees,
                                                 single_threshold_rupees, active)
                        VALUES (:code, :name, :rate, :noPanRate, :yearThreshold, :singleThreshold, :active)
                        RETURNING id
                        """)
                .param("code", form.code().trim())
                .param("name", form.name().trim())
                .param("rate", form.ratePercent())
                .param("noPanRate", form.noPanRatePercent())
                .param("yearThreshold", threshold(form.yearThresholdRupees()))
                .param("singleThreshold", threshold(form.singleThresholdRupees()))
                .param("active", form.active())
                .query(Long.class)
                .single();
        return section(id);
    }

    @Transactional
    public TdsSectionView updateSection(long id, TdsSectionForm form) {
        section(id);
        checkCodeFree(form.code().trim(), id);
        jdbc.sql("""
                        UPDATE tds_section
                        SET code = :code, name = :name, rate_percent = :rate,
                            no_pan_rate_percent = :noPanRate, year_threshold_rupees = :yearThreshold,
                            single_threshold_rupees = :singleThreshold, active = :active
                        WHERE id = :id
                        """)
                .param("code", form.code().trim())
                .param("name", form.name().trim())
                .param("rate", form.ratePercent())
                .param("noPanRate", form.noPanRatePercent())
                .param("yearThreshold", threshold(form.yearThresholdRupees()))
                .param("singleThreshold", threshold(form.singleThresholdRupees()))
                .param("active", form.active())
                .param("id", id)
                .update();
        return section(id);
    }

    @Transactional
    public TdsChallanView createChallan(TdsChallanForm form, @Nullable String actor) {
        var month = month(form.periodMonth());
        if (!MODES.contains(form.mode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick how it was paid");
        }
        boolean sectionExists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM tds_section WHERE id = :id)")
                .param("id", form.tdsSectionId())
                .query(Boolean.class)
                .single();
        if (!sectionExists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a TDS section");
        }
        long id = jdbc.sql("""
                        INSERT INTO tds_challan (tds_section_id, period_month, amount_rupees, interest_rupees,
                                                 challan_no, bsr_code, paid_on, mode, created_by)
                        VALUES (:section, :month, :amount, :interest, :challanNo, :bsr, :paidOn, :mode, :by)
                        RETURNING id
                        """)
                .param("section", form.tdsSectionId())
                .param("month", month.atDay(1))
                .param("amount", Rupees.of(form.amountRupees()))
                .param("interest", Rupees.of(form.interestRupees()))
                .param("challanNo", form.challanNo().trim())
                .param("bsr", form.bsrCode().trim())
                .param("paidOn", form.paidOn())
                .param("mode", form.mode())
                .param("by", actor)
                .query(Long.class)
                .single();
        return challan(id);
    }

    @Transactional(readOnly = true)
    public List<TdsChallanView> challans(LocalDate from, LocalDate to) {
        return jdbc
                .sql(CHALLAN_SELECT + " WHERE c.paid_on BETWEEN :from AND :to ORDER BY c.paid_on DESC, c.id DESC")
                .param("from", from)
                .param("to", to)
                .query(ChallanRow.class)
                .list()
                .stream()
                .map(ChallanRow::toView)
                .toList();
    }

    /** One row per section and month in the range that had a deduction or a challan. */
    @Transactional(readOnly = true)
    public List<TdsMonthSummary> summary(LocalDate from, LocalDate to) {
        var fromMonth = from.withDayOfMonth(1);
        var toMonth = to.withDayOfMonth(1);
        return jdbc
                .sql("""
                        WITH deducted AS (
                            SELECT tds_section_id AS section_id, date_trunc('month', bill_date)::date AS month,
                                   SUM(tds_rupees) AS amount
                            FROM supplier_bill
                            WHERE status = 'OPEN' AND tds_rupees > 0
                              AND bill_date >= :fromMonth AND bill_date < :afterToMonth
                            GROUP BY 1, 2
                        ), paid AS (
                            SELECT tds_section_id AS section_id, period_month AS month, SUM(amount_rupees) AS amount
                            FROM tds_challan
                            WHERE period_month BETWEEN :fromMonth AND :toMonth
                            GROUP BY 1, 2
                        )
                        SELECT s.code AS section_code, s.name AS section_name, COALESCE(d.month, p.month) AS month,
                               COALESCE(d.amount, 0) AS deducted_rupees, COALESCE(p.amount, 0) AS paid_rupees
                        FROM deducted d
                        FULL OUTER JOIN paid p ON p.section_id = d.section_id AND p.month = d.month
                        JOIN tds_section s ON s.id = COALESCE(d.section_id, p.section_id)
                        ORDER BY 3, 1
                        """)
                .param("fromMonth", fromMonth)
                .param("afterToMonth", toMonth.plusMonths(1))
                .param("toMonth", toMonth)
                .query(SummaryRow.class)
                .list()
                .stream()
                .map(SummaryRow::toSummary)
                .toList();
    }

    private TdsSectionView section(long id) {
        return jdbc.sql(SECTION_SELECT + " WHERE id = :id")
                .param("id", id)
                .query(TdsSectionView.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
    }

    private TdsChallanView challan(long id) {
        return jdbc.sql(CHALLAN_SELECT + " WHERE c.id = :id")
                .param("id", id)
                .query(ChallanRow.class)
                .single()
                .toView();
    }

    private void checkCodeFree(String code, long exceptId) {
        boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM tds_section WHERE code = :code AND id <> :id)")
                .param("code", code)
                .param("id", exceptId)
                .query(Boolean.class)
                .single();
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A section with this code exists");
        }
    }

    private static @Nullable BigDecimal threshold(@Nullable BigDecimal amount) {
        return amount == null ? null : Rupees.of(amount);
    }

    private static YearMonth month(String text) {
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a month like 2026-11", e);
        }
    }
}

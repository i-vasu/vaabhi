package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The deductee and challan data behind the quarterly 26Q TDS return. */
@Service
@Transactional(readOnly = true)
public class TdsReturnService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEDUCTEE_SELECT = """
            SELECT b.id AS bill_id, s.code AS section_code, p.name AS party_name, NULLIF(TRIM(p.pan), '') AS pan,
                   b.bill_no, b.bill_date AS credited_on, b.taxable_rupees AS amount_rupees, b.tds_rupees,
                   (SELECT c.challan_no FROM tds_challan c
                    WHERE c.tds_section_id = b.tds_section_id
                      AND c.period_month = date_trunc('month', b.bill_date)::date
                    ORDER BY c.paid_on, c.id
                    LIMIT 1) AS challan_no
            FROM supplier_bill b
            JOIN tds_section s ON s.id = b.tds_section_id
            JOIN party p ON p.id = b.party_id
            """;
    private static final String CHALLAN_SELECT = """
            SELECT c.id, s.code AS section_code, c.period_month, c.amount_rupees, c.interest_rupees,
                   c.challan_no, c.bsr_code, c.paid_on
            FROM tds_challan c
            JOIN tds_section s ON s.id = c.tds_section_id
            """;

    private final JdbcClient jdbc;

    TdsReturnService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record DeducteeRow(
            long billId,
            String sectionCode,
            String partyName,
            @Nullable String pan,
            String billNo,
            LocalDate creditedOn,
            BigDecimal amountRupees,
            BigDecimal tdsRupees,
            @Nullable String challanNo) {

        TdsDeducteeRow toRow() {
            return new TdsDeducteeRow(
                    billId,
                    sectionCode,
                    partyName,
                    pan,
                    billNo,
                    creditedOn,
                    Rupees.of(amountRupees),
                    ratePercent(),
                    Rupees.of(tdsRupees),
                    challanNo);
        }

        private BigDecimal ratePercent() {
            if (amountRupees.signum() == 0) {
                return Rupees.ZERO;
            }
            return tdsRupees.multiply(HUNDRED).divide(amountRupees, Rupees.SCALE, RoundingMode.HALF_UP);
        }
    }

    public TdsQuarter quarter(LocalDate fyStart, int quarter) {
        if (quarter < 1 || quarter > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quarter must be 1 to 4");
        }
        var from = fyStart.plusMonths(3L * (quarter - 1));
        var to = from.plusMonths(3).minusDays(1);
        var deductees =
                jdbc
                        .sql(DEDUCTEE_SELECT + """
                        WHERE b.tds_rupees > 0 AND b.status = 'OPEN' AND b.bill_date BETWEEN :from AND :to
                          AND EXISTS (SELECT 1 FROM journal_entry e
                                      WHERE e.source_type = 'SUPPLIER_BILL' AND e.source_id = b.id
                                        AND e.source_step = 'ISSUE')
                        ORDER BY s.code, b.bill_date, b.id
                        """)
                        .param("from", from)
                        .param("to", to)
                        .query(DeducteeRow.class)
                        .list()
                        .stream()
                        .map(DeducteeRow::toRow)
                        .toList();
        var challans = jdbc.sql(CHALLAN_SELECT + """
                        WHERE c.period_month BETWEEN :from AND :to
                        ORDER BY s.code, c.period_month, c.paid_on, c.id
                        """)
                .param("from", from)
                .param("to", to)
                .query(TdsQuarterChallan.class)
                .list();
        return new TdsQuarter(
                FiscalYears.label(fyStart), quarter, from, to, deductees, challans, totals(deductees, challans));
    }

    public String csv(LocalDate fyStart, int quarter, String part) {
        if (!part.equals("deductees") && !part.equals("challans")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Part must be deductees or challans");
        }
        var data = quarter(fyStart, quarter);
        var csv = new StringBuilder();
        if (part.equals("deductees")) {
            csv.append("section,party,pan,bill_no,credited_on,amount,rate_percent,tds,challan_no\n");
            for (var row : data.deductees()) {
                appendLine(
                        csv,
                        row.sectionCode(),
                        row.partyName(),
                        row.pan(),
                        row.billNo(),
                        row.creditedOn(),
                        row.amountRupees(),
                        row.ratePercent(),
                        row.tdsRupees(),
                        row.challanNo());
            }
        } else {
            csv.append("section,period_month,amount,interest,challan_no,bsr_code,paid_on\n");
            for (var challan : data.challans()) {
                appendLine(
                        csv,
                        challan.sectionCode(),
                        challan.periodMonth(),
                        challan.amountRupees(),
                        challan.interestRupees(),
                        challan.challanNo(),
                        challan.bsrCode(),
                        challan.paidOn());
            }
        }
        return csv.toString();
    }

    private static List<TdsSectionTotal> totals(List<TdsDeducteeRow> deductees, List<TdsQuarterChallan> challans) {
        Map<String, BigDecimal> deducted = new TreeMap<>();
        Map<String, BigDecimal> deposited = new TreeMap<>();
        for (var row : deductees) {
            deducted.merge(row.sectionCode(), row.tdsRupees(), BigDecimal::add);
        }
        for (var challan : challans) {
            deposited.merge(challan.sectionCode(), challan.amountRupees(), BigDecimal::add);
        }
        var codes = new TreeSet<>(deducted.keySet());
        codes.addAll(deposited.keySet());
        return codes.stream()
                .map(code -> {
                    var deductedRupees = Rupees.of(deducted.getOrDefault(code, BigDecimal.ZERO));
                    var depositedRupees = Rupees.of(deposited.getOrDefault(code, BigDecimal.ZERO));
                    return new TdsSectionTotal(
                            code, deductedRupees, depositedRupees, Rupees.of(deductedRupees.subtract(depositedRupees)));
                })
                .toList();
    }

    private static void appendLine(StringBuilder csv, @Nullable Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(cell(values[i]));
        }
        csv.append('\n');
    }

    private static String cell(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        var text = value instanceof BigDecimal amount ? amount.toPlainString() : value.toString();
        if (text.contains(",") || text.contains("\"")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}

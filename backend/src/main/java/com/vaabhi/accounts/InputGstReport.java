package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Input GST claimed on supplier bills and paid inside Razorpay fees, month by month. */
@Service
@Transactional(readOnly = true)
public class InputGstReport {

    private static final String MONTHS_SQL = """
            WITH bills AS (
                SELECT date_trunc('month', b.bill_date)::date AS month,
                       SUM(l.taxable_rupees) AS taxable,
                       SUM(l.cgst_rupees) AS cgst,
                       SUM(l.sgst_rupees) AS sgst,
                       SUM(l.igst_rupees) AS igst
                FROM supplier_bill b
                JOIN supplier_bill_line l ON l.bill_id = b.id
                WHERE b.status = 'OPEN' AND b.bill_date BETWEEN :from AND :to
                GROUP BY 1
            ), gateway AS (
                SELECT date_trunc('month', settled_on)::date AS month, SUM(tax_rupees) AS gst
                FROM razorpay_settlement
                WHERE settled_on BETWEEN :from AND :to
                GROUP BY 1
            )
            SELECT COALESCE(b.month, g.month) AS month,
                   COALESCE(b.taxable, 0) AS taxable_rupees,
                   COALESCE(b.cgst, 0) AS cgst_rupees,
                   COALESCE(b.sgst, 0) AS sgst_rupees,
                   COALESCE(b.igst, 0) AS igst_rupees,
                   COALESCE(g.gst, 0) AS gateway_gst_rupees
            FROM bills b
            FULL OUTER JOIN gateway g ON g.month = b.month
            ORDER BY 1
            """;

    private final JdbcClient jdbc;

    InputGstReport(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record MonthRow(
            LocalDate month,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees,
            BigDecimal gatewayGstRupees) {

        InputGstMonth toMonth() {
            var cgst = Rupees.of(cgstRupees);
            var sgst = Rupees.of(sgstRupees);
            var igst = Rupees.of(igstRupees);
            var gateway = Rupees.of(gatewayGstRupees);
            return new InputGstMonth(
                    YearMonth.from(month),
                    Rupees.of(taxableRupees),
                    cgst,
                    sgst,
                    igst,
                    gateway,
                    Rupees.of(cgst.add(sgst).add(igst).add(gateway)));
        }
    }

    public List<InputGstMonth> months(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return jdbc.sql(MONTHS_SQL)
                .param("from", from)
                .param("to", to)
                .query(MonthRow.class)
                .list()
                .stream()
                .map(MonthRow::toMonth)
                .toList();
    }

    /** The report as a CSV the owner can open in a spreadsheet. */
    public String csv(LocalDate from, LocalDate to) {
        var rows = months(from, to);
        var writer = new StringWriter();
        try (var printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().build())) {
            printer.printRecord("Month", "Taxable", "CGST", "SGST", "IGST", "Gateway GST", "Total");
            for (var row : rows) {
                printer.printRecord(
                        row.month(),
                        row.taxableRupees().toPlainString(),
                        row.cgstRupees().toPlainString(),
                        row.sgstRupees().toPlainString(),
                        row.igstRupees().toPlainString(),
                        row.gatewayGstRupees().toPlainString(),
                        row.totalRupees().toPlainString());
            }
            printer.printRecord(
                    "Total",
                    Rupees.sum(rows, InputGstMonth::taxableRupees).toPlainString(),
                    Rupees.sum(rows, InputGstMonth::cgstRupees).toPlainString(),
                    Rupees.sum(rows, InputGstMonth::sgstRupees).toPlainString(),
                    Rupees.sum(rows, InputGstMonth::igstRupees).toPlainString(),
                    Rupees.sum(rows, InputGstMonth::gatewayGstRupees).toPlainString(),
                    Rupees.sum(rows, InputGstMonth::totalRupees).toPlainString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From is after To");
        }
    }
}

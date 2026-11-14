package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The input GST report: tax on open supplier bills and gateway GST, summed by month. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class InputGstReportIT extends BooksFixtures {

    /** Far enough back that no other test's rows land in the range. */
    private static final LocalDate FROM = LocalDate.of(2019, 5, 1);

    private static final LocalDate TO = LocalDate.of(2019, 5, 31);

    @Autowired
    private InputGstReport report;

    private long supplier() {
        return jdbc.sql("INSERT INTO party (name, is_supplier) VALUES (:name, TRUE) RETURNING id")
                .param("name", unique("GST supplier "))
                .query(Long.class)
                .single();
    }

    /** A bill with one EXPENSE line; the bill totals follow from the line's tax. */
    private void bill(
            long party,
            String billNo,
            String billDate,
            String taxable,
            String cgst,
            String sgst,
            String igst,
            String status) {
        var taxableAmount = new BigDecimal(taxable);
        var total = taxableAmount
                .add(new BigDecimal(cgst))
                .add(new BigDecimal(sgst))
                .add(new BigDecimal(igst));
        long bill = jdbc.sql("""
                        INSERT INTO supplier_bill (party_id, bill_no, bill_date, taxable_rupees, tax_rupees,
                                                   total_rupees, payable_rupees, status, cancelled_on)
                        VALUES (:party, :billNo, CAST(:billDate AS date), CAST(:taxable AS numeric),
                                CAST(:tax AS numeric), CAST(:total AS numeric), CAST(:total AS numeric), :status,
                                CASE WHEN CAST(:status AS text) = 'CANCELLED' THEN CAST(:billDate AS date) END)
                        RETURNING id
                        """)
                .param("party", party)
                .param("billNo", billNo)
                .param("billDate", billDate)
                .param("taxable", taxable)
                .param("tax", total.subtract(taxableAmount))
                .param("total", total)
                .param("status", status)
                .query(Long.class)
                .single();
        jdbc.sql("""
                        INSERT INTO supplier_bill_line (bill_id, position, kind, description, account_id,
                                                        taxable_rupees, gst_rate_percent, cgst_rupees, sgst_rupees,
                                                        igst_rupees, total_rupees)
                        VALUES (:bill, 1, 'EXPENSE', 'Books test', :account, CAST(:taxable AS numeric), 5,
                                CAST(:cgst AS numeric), CAST(:sgst AS numeric), CAST(:igst AS numeric),
                                CAST(:total AS numeric))
                        """)
                .param("bill", bill)
                .param("account", accountId("PURCHASES"))
                .param("taxable", taxable)
                .param("cgst", cgst)
                .param("sgst", sgst)
                .param("igst", igst)
                .param("total", total)
                .update();
    }

    private void gateway(String settledOn, String tax) {
        jdbc.sql("""
                        INSERT INTO razorpay_settlement (id, settled_on, tax_rupees)
                        VALUES (:id, CAST(:settledOn AS date), CAST(:tax AS numeric))
                        """)
                .param("id", unique("SETTLE-"))
                .param("settledOn", settledOn)
                .param("tax", tax)
                .update();
    }

    @Test
    void sumsBillsAndGatewayByMonth() {
        long party = supplier();
        bill(party, "GST-1", "2019-05-10", "1000", "25", "25", "0", "OPEN");
        bill(party, "GST-2", "2019-05-12", "500", "0", "0", "90", "CANCELLED");
        gateway("2019-05-20", "18");

        var months = report.months(FROM, TO);

        assertThat(months).hasSize(1);
        var month = months.getFirst();
        assertThat(month.month()).isEqualTo(YearMonth.of(2019, 5));
        assertThat(month.taxableRupees()).isEqualByComparingTo("1000.00");
        assertThat(month.cgstRupees()).isEqualByComparingTo("25.00");
        assertThat(month.sgstRupees()).isEqualByComparingTo("25.00");
        assertThat(month.igstRupees()).isEqualByComparingTo("0.00");
        assertThat(month.gatewayGstRupees()).isEqualByComparingTo("18.00");
        assertThat(month.totalRupees()).isEqualByComparingTo("68.00");
    }

    @Test
    void cancelledBillsLeftOut() {
        long party = supplier();
        // June, not May: the other test in this class writes May 2019 rows to the shared database.
        bill(party, "GST-CANCELLED", "2019-06-10", "500", "0", "0", "90", "CANCELLED");

        assertThat(report.months(LocalDate.of(2019, 6, 1), LocalDate.of(2019, 6, 30))).isEmpty();
    }
}

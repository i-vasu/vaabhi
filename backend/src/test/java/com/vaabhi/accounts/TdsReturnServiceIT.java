package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The quarter's 26Q deductees, challans and section totals, against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class TdsReturnServiceIT extends BooksFixtures {

    private static final LocalDate FY_START = LocalDate.of(2073, 4, 1);
    private static final LocalDate MAY = LocalDate.of(2073, 5, 10);

    @Autowired
    private TdsReturnService returns;

    @Autowired
    private TdsService tds;

    @Autowired
    private JournalService journal;

    private static TdsSectionForm sectionForm(String code) {
        return new TdsSectionForm(code, "Test section", new BigDecimal("1"), new BigDecimal("20"), null, null, true);
    }

    private long supplier(@Nullable String pan) {
        return jdbc.sql("INSERT INTO party (name, pan, is_supplier) VALUES (:name, :pan, TRUE) RETURNING id")
                .param("name", unique("TQR supplier "))
                .param("pan", pan)
                .query(Long.class)
                .single();
    }

    private long bill(long party, long section, LocalDate date, BigDecimal taxable, BigDecimal tds) {
        return jdbc.sql("""
                        INSERT INTO supplier_bill (party_id, bill_no, bill_date, taxable_rupees, tax_rupees,
                                                   total_rupees, tds_section_id, tds_rupees, payable_rupees)
                        VALUES (:party, :number, :date, :taxable, 0, :taxable, :section, :tds, :payable)
                        RETURNING id
                        """)
                .param("party", party)
                .param("number", unique("TQR-BILL-"))
                .param("date", date)
                .param("taxable", taxable)
                .param("section", section)
                .param("tds", tds)
                .param("payable", taxable.subtract(tds))
                .query(Long.class)
                .single();
    }

    private void cancelledBill(long party, long section, LocalDate date, BigDecimal taxable, BigDecimal tds) {
        jdbc.sql("""
                        INSERT INTO supplier_bill (party_id, bill_no, bill_date, taxable_rupees, tax_rupees,
                                                   total_rupees, tds_section_id, tds_rupees, payable_rupees,
                                                   status, cancelled_on)
                        VALUES (:party, :number, :date, :taxable, 0, :taxable, :section, :tds, :payable,
                                'CANCELLED', :date)
                        """)
                .param("party", party)
                .param("number", unique("TQR-BILL-"))
                .param("date", date)
                .param("taxable", taxable)
                .param("section", section)
                .param("tds", tds)
                .param("payable", taxable.subtract(tds))
                .update();
    }

    private void post(long billId, LocalDate date, BigDecimal taxable) {
        journal.post(
                new NewEntry(
                        date,
                        "SUPPLIER_BILL",
                        billId,
                        "ISSUE",
                        "Test bill",
                        null,
                        null,
                        List.of(
                                new NewEntryLine(accountId("EXPENSE_RENT"), null, taxable, Rupees.ZERO, null),
                                new NewEntryLine(accountId("CREDITORS"), null, Rupees.ZERO, taxable, null))),
                "test");
    }

    @Test
    void quarterListsDeducteesChallansAndTotals() {
        var section = tds.createSection(sectionForm(unique("TQ")));
        long withPan = supplier("ABCDE1234F");
        long withoutPan = supplier(null);
        long first = bill(withPan, section.id(), MAY, new BigDecimal("2000"), new BigDecimal("100"));
        long second = bill(withoutPan, section.id(), MAY.plusDays(1), new BigDecimal("1000"), new BigDecimal("50"));
        post(first, MAY, new BigDecimal("2000"));
        post(second, MAY.plusDays(1), new BigDecimal("1000"));
        cancelledBill(withPan, section.id(), MAY.plusDays(2), new BigDecimal("500"), new BigDecimal("25"));
        jdbc.sql("""
                        INSERT INTO tds_challan (tds_section_id, period_month, amount_rupees, challan_no, bsr_code,
                                                 paid_on, mode)
                        VALUES (:section, :month, :amount, '12345', '0510001', :paidOn, 'UPI')
                        """)
                .param("section", section.id())
                .param("month", LocalDate.of(2073, 5, 1))
                .param("amount", new BigDecimal("120"))
                .param("paidOn", LocalDate.of(2073, 6, 7))
                .update();

        var quarter = returns.quarter(FY_START, 1);

        assertThat(quarter.fy()).isEqualTo("2073-74");
        assertThat(quarter.quarter()).isEqualTo(1);
        assertThat(quarter.from()).isEqualTo(LocalDate.of(2073, 4, 1));
        assertThat(quarter.to()).isEqualTo(LocalDate.of(2073, 6, 30));

        var rows = quarter.deductees().stream()
                .filter(row -> row.sectionCode().equals(section.code()))
                .toList();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(TdsDeducteeRow::pan).containsExactlyInAnyOrder("ABCDE1234F", null);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.challanNo()).isEqualTo("12345");
            assertThat(row.ratePercent()).isEqualByComparingTo("5");
        });

        var total = quarter.totals().stream()
                .filter(t -> t.sectionCode().equals(section.code()))
                .findFirst()
                .orElseThrow();
        assertThat(total.deductedRupees()).isEqualByComparingTo("150");
        assertThat(total.depositedRupees()).isEqualByComparingTo("120");
        assertThat(total.differenceRupees()).isEqualByComparingTo("30");
    }

    @Test
    void fiveIsNotAQuarter() {
        assertThatThrownBy(() -> returns.quarter(FY_START, 5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Quarter must be 1 to 4");
    }

    @Test
    void csvRejectsUnknownPartAndStartsWithHeader() {
        assertThatThrownBy(() -> returns.csv(FY_START, 1, "x"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Part must be deductees or challans");
        assertThat(returns.csv(FY_START, 1, "deductees"))
                .startsWith("section,party,pan,bill_no,credited_on,amount,rate_percent,tds,challan_no\n");
    }
}

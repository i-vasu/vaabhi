package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Gstr3bSetOffTest {

    private static Gstr3bSetOff head(List<Gstr3bSetOff> rows, String head) {
        return rows.stream().filter(row -> row.head().equals(head)).findFirst().orElseThrow();
    }

    @Test
    void igstCreditPaysIgstThenCgstThenSgst() {
        var rows = Gstr3bService.setOff(
                new BigDecimal("100"),
                new BigDecimal("50"),
                new BigDecimal("50"),
                new BigDecimal("120"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(rows).extracting(Gstr3bSetOff::head).containsExactly("IGST", "CGST", "SGST");

        var igst = head(rows, "IGST");
        assertThat(igst.outputRupees()).isEqualByComparingTo("100");
        assertThat(igst.itcUsedRupees()).isEqualByComparingTo("100");
        assertThat(igst.cashRupees()).isEqualByComparingTo("0");
        assertThat(igst.carriedForwardRupees()).isEqualByComparingTo("0");

        var cgst = head(rows, "CGST");
        assertThat(cgst.outputRupees()).isEqualByComparingTo("50");
        assertThat(cgst.itcUsedRupees()).isEqualByComparingTo("20");
        assertThat(cgst.cashRupees()).isEqualByComparingTo("30");
        assertThat(cgst.carriedForwardRupees()).isEqualByComparingTo("0");

        var sgst = head(rows, "SGST");
        assertThat(sgst.outputRupees()).isEqualByComparingTo("50");
        assertThat(sgst.itcUsedRupees()).isEqualByComparingTo("0");
        assertThat(sgst.cashRupees()).isEqualByComparingTo("50");
        assertThat(sgst.carriedForwardRupees()).isEqualByComparingTo("0");
    }

    @Test
    void ownCreditPaysOwnHeadAndCarriesTheRest() {
        var rows = Gstr3bService.setOff(
                BigDecimal.ZERO,
                new BigDecimal("10"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("40"),
                new BigDecimal("5"));

        assertThat(rows).extracting(Gstr3bSetOff::head).containsExactly("IGST", "CGST", "SGST");

        var igst = head(rows, "IGST");
        assertThat(igst.outputRupees()).isEqualByComparingTo("0");
        assertThat(igst.itcUsedRupees()).isEqualByComparingTo("0");
        assertThat(igst.cashRupees()).isEqualByComparingTo("0");
        assertThat(igst.carriedForwardRupees()).isEqualByComparingTo("0");

        var cgst = head(rows, "CGST");
        assertThat(cgst.outputRupees()).isEqualByComparingTo("10");
        assertThat(cgst.itcUsedRupees()).isEqualByComparingTo("10");
        assertThat(cgst.cashRupees()).isEqualByComparingTo("0");
        assertThat(cgst.carriedForwardRupees()).isEqualByComparingTo("30");

        var sgst = head(rows, "SGST");
        assertThat(sgst.outputRupees()).isEqualByComparingTo("10");
        assertThat(sgst.itcUsedRupees()).isEqualByComparingTo("5");
        assertThat(sgst.cashRupees()).isEqualByComparingTo("5");
        assertThat(sgst.carriedForwardRupees()).isEqualByComparingTo("0");

        var amounts = rows.stream()
                .flatMap(row -> Stream.of(
                        row.outputRupees(), row.itcUsedRupees(), row.cashRupees(), row.carriedForwardRupees()))
                .toList();
        assertThat(amounts).allMatch(amount -> amount.signum() >= 0);
    }

    @Test
    void noTaxMeansThreeZeroRows() {
        var rows = Gstr3bService.setOff(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(rows).extracting(Gstr3bSetOff::head).containsExactly("IGST", "CGST", "SGST");
        for (var row : rows) {
            assertThat(row.outputRupees()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(row.itcUsedRupees()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(row.cashRupees()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(row.carriedForwardRupees()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}

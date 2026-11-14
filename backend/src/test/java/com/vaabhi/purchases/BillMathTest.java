package com.vaabhi.purchases;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The bill arithmetic: the GST split, TDS and the financial year. */
class BillMathTest {

    @Test
    void intraStateSplitsOddPaisaToSgst() {
        var tax = BillMath.tax(new BigDecimal("100.10"), new BigDecimal("5"), false);

        assertThat(tax.cgst()).isEqualByComparingTo("2.50");
        assertThat(tax.sgst()).isEqualByComparingTo("2.51");
        assertThat(tax.igst()).isEqualByComparingTo("0");
        assertThat(tax.total()).isEqualByComparingTo("105.11");
    }

    @Test
    void interStateIsAllIgst() {
        var tax = BillMath.tax(new BigDecimal("1000"), new BigDecimal("12"), true);

        assertThat(tax.cgst()).isEqualByComparingTo("0");
        assertThat(tax.sgst()).isEqualByComparingTo("0");
        assertThat(tax.igst()).isEqualByComparingTo("120.00");
        assertThat(tax.total()).isEqualByComparingTo("1120.00");
    }

    @Test
    void blankStateIsIntraState() {
        assertThat(BillMath.interState(null, "33")).isFalse();
        assertThat(BillMath.interState("29", "33")).isTrue();
    }

    @Test
    void tdsRounds() {
        assertThat(BillMath.tds(new BigDecimal("12345.67"), new BigDecimal("2")))
                .isEqualByComparingTo("246.91");
    }

    @Test
    void fyStart() {
        assertThat(BillMath.fyStart(LocalDate.of(2027, 3, 31))).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(BillMath.fyStart(LocalDate.of(2026, 4, 1))).isEqualTo(LocalDate.of(2026, 4, 1));
    }
}

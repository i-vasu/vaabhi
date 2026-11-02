package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** GST arithmetic on invoices. */
class InvoiceTaxTest {

    @Test
    void takesTaxOutOfInclusivePrices() {
        assertThat(InvoiceService.tax(105_000, new BigDecimal("5"), true))
                .isEqualTo(new InvoiceService.Tax(100_000, 2_500, 2_500, 105_000));
    }

    @Test
    void addsTaxOnTopOfExclusivePrices() {
        assertThat(InvoiceService.tax(100_000, new BigDecimal("12"), false))
                .isEqualTo(new InvoiceService.Tax(100_000, 6_000, 6_000, 112_000));
    }

    @Test
    void givesTheOddPaisaToSgstAndRoundsHalfUp() {
        // 999 at 5 % inclusive: taxable 951.43 → 951, GST 48 → 24 + 24. 1,001 at 5 % exclusive: GST 50.05 → 50.
        // 1,010 at 2.5 % exclusive: GST 25.25 → 25 → CGST 12 + SGST 13.
        assertThat(InvoiceService.tax(999, new BigDecimal("5"), true))
                .isEqualTo(new InvoiceService.Tax(951, 24, 24, 999));
        assertThat(InvoiceService.tax(1_001, new BigDecimal("5"), false))
                .isEqualTo(new InvoiceService.Tax(1_001, 25, 25, 1_051));
        assertThat(InvoiceService.tax(1_010, new BigDecimal("2.5"), false))
                .isEqualTo(new InvoiceService.Tax(1_010, 12, 13, 1_035));
    }

    @Test
    void noTaxWithoutARate() {
        assertThat(InvoiceService.tax(50_000, BigDecimal.ZERO, true))
                .isEqualTo(new InvoiceService.Tax(50_000, 0, 0, 50_000));
    }
}

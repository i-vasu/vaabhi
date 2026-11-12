package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** GST arithmetic on invoices (amounts in rupees, to the paisa). */
class InvoiceTaxTest {

    @Test
    void takesTaxOutOfInclusivePrices() {
        assertThat(TaxEngine.lineTax(rupees("1050"), new BigDecimal("5"), true))
                .isEqualTo(tax("1000.00", "25.00", "25.00", "1050.00"));
    }

    @Test
    void addsTaxOnTopOfExclusivePrices() {
        assertThat(TaxEngine.lineTax(rupees("1000"), new BigDecimal("12"), false))
                .isEqualTo(tax("1000.00", "60.00", "60.00", "1120.00"));
    }

    @Test
    void givesTheOddPaisaToSgstAndRoundsHalfUp() {
        // ₹9.99 at 5 % inclusive: taxable 9.5143 → 9.51, GST 0.48 → 0.24 + 0.24. ₹10.01 at 5 % exclusive: GST
        // 0.5005 → 0.50. ₹10.10 at 2.5 % exclusive: GST 0.2525 → 0.25 → CGST 0.12 + SGST 0.13.
        assertThat(TaxEngine.lineTax(rupees("9.99"), new BigDecimal("5"), true))
                .isEqualTo(tax("9.51", "0.24", "0.24", "9.99"));
        assertThat(TaxEngine.lineTax(rupees("10.01"), new BigDecimal("5"), false))
                .isEqualTo(tax("10.01", "0.25", "0.25", "10.51"));
        assertThat(TaxEngine.lineTax(rupees("10.10"), new BigDecimal("2.5"), false))
                .isEqualTo(tax("10.10", "0.12", "0.13", "10.35"));
    }

    @Test
    void noTaxWithoutARate() {
        assertThat(TaxEngine.lineTax(rupees("500"), BigDecimal.ZERO, true))
                .isEqualTo(tax("500.00", "0.00", "0.00", "500.00"));
    }

    private static BigDecimal rupees(String amount) {
        return new BigDecimal(amount);
    }

    private static TaxEngine.Tax tax(String taxable, String cgst, String sgst, String total) {
        return new TaxEngine.Tax(
                new BigDecimal(taxable), new BigDecimal(cgst), new BigDecimal(sgst), new BigDecimal(total));
    }
}

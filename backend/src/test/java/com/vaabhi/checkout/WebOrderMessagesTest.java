package com.vaabhi.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** How order messages show amounts and fill in what is missing. */
class WebOrderMessagesTest {

    @Test
    void showsRupeesWithIndianGroupingAndPaiseOnlyWhenThereAreSome() {
        assertThat(WebOrderMessages.rupees(new BigDecimal("24900.00"))).isEqualTo("24,900");
        assertThat(WebOrderMessages.rupees(new BigDecimal("125000"))).isEqualTo("1,25,000");
        assertThat(WebOrderMessages.rupees(new BigDecimal("1250.50"))).isEqualTo("1,250.50");
        assertThat(WebOrderMessages.rupees(new BigDecimal("12345678.9"))).isEqualTo("1,23,45,678.90");
        assertThat(WebOrderMessages.rupees(new BigDecimal("999"))).isEqualTo("999");
        assertThat(WebOrderMessages.rupees(null)).isEqualTo("0");
    }

    @Test
    void fillsInTheCourierAndTrackingWhenThereAreNone() {
        var event = new WebOrderEvent(
                WebOrderEvent.Type.PLACED,
                "WEB-1001",
                "+919876501234",
                new BigDecimal("1699.00"),
                WebOrder.PaymentMethod.COD,
                null,
                null,
                null);
        assertThat(WebOrderMessages.variables(event))
                .containsEntry("order", "WEB-1001")
                .containsEntry("amount", "1,699")
                .containsEntry("payment", "Cash on delivery")
                .containsEntry("courier", "our delivery partner")
                .containsEntry("tracking", "-")
                .containsEntry("refund", "0");
    }
}

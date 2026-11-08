package com.vaabhi.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShopContactTest {

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "98765 43210, 919876543210",
                "+91 98765 43210, 919876543210",
                "+44 20 7946 0958, 442079460958",
                "12345, null",
                "null, null"
            })
    void whatsappNumberHasTheCountryCode(String phone, String expected) {
        assertThat(ShopContact.whatsappNumber(phone)).isEqualTo(expected);
    }
}

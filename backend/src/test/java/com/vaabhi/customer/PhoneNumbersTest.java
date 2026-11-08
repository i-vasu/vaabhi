package com.vaabhi.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"9876543210", "98765 43210", "098765-43210", "919876543210", "+91 98765 43210", "0091 9876543210"
            })
    void acceptsCommonWaysOfWritingAnIndianMobile(String input) {
        assertThat(PhoneNumbers.normalize(input)).contains("+919876543210");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "5876543210", "98765432101", "+44 7700 900123", "+1 202 555 0143", "abc"})
    void rejectsEverythingElse(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void masksForLogs() {
        assertThat(PhoneNumbers.masked("+919876543210")).isEqualTo("+91******3210");
    }
}

package com.vaabhi.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What {@link RazorpayClient#errorDescription} makes of Razorpay's refusal bodies. */
class RazorpayClientTest {

    @Test
    void noBodyIsNull() {
        assertThat(RazorpayClient.errorDescription(null)).isNull();
        assertThat(RazorpayClient.errorDescription("   ")).isNull();
    }

    @Test
    void bodyThatIsNotJsonIsNull() {
        assertThat(RazorpayClient.errorDescription("not json")).isNull();
    }

    @Test
    void jsonWithoutAnErrorDescriptionIsNull() {
        assertThat(RazorpayClient.errorDescription("{}")).isNull();
        assertThat(RazorpayClient.errorDescription("{\"error\":{}}")).isNull();
        assertThat(RazorpayClient.errorDescription("{\"error\":{\"code\":\"BAD_REQUEST\"}}"))
                .isNull();
    }

    @Test
    void descriptionIsReadAndItsTrailingDotDropped() {
        assertThat(RazorpayClient.errorDescription("{\"error\":{\"description\":\"The amount is invalid.\"}}"))
                .isEqualTo("The amount is invalid");
    }

    @Test
    void descriptionIsStrippedOfSurroundingWhitespace() {
        assertThat(RazorpayClient.errorDescription("{\"error\":{\"description\":\"  The amount is invalid.  \"}}"))
                .isEqualTo("The amount is invalid");
    }

    @Test
    void descriptionIsCutTo120Chars() {
        assertThat(RazorpayClient.errorDescription("{\"error\":{\"description\":\"" + "a".repeat(200) + "\"}}"))
                .hasSize(120)
                .isEqualTo("a".repeat(120));
    }
}

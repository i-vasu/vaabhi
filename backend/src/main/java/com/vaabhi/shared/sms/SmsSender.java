package com.vaabhi.shared.sms;

import java.util.Map;

/** Sends one templated SMS. Implementations must not log message variables in production (they hold codes). */
public interface SmsSender {

    /** Template for sign-in codes; its variable is {@code otp}. */
    String OTP_TEMPLATE = "otp";

    /** False when the provider has no credentials yet; callers answer 503 instead of sending. */
    boolean configured();

    /**
     * @param phone E.164 number, e.g. {@code +919876543210}
     * @param template logical template name, e.g. {@link #OTP_TEMPLATE}; mapped to the provider's template id
     * @param variables values for the template's placeholders
     * @throws SmsException when the provider refuses or cannot be reached
     */
    void send(String phone, String template, Map<String, String> variables);
}

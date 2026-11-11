package com.vaabhi.shared.whatsapp;

import java.util.Map;

/**
 * Sends one approved WhatsApp template. Callers name a logical template and its variables; the provider maps them to
 * the approved template and fills {{1}}, {{2}}, … in the configured order. Implementations must not log message
 * variables in production (they hold sign-in codes).
 */
public interface WhatsAppSender {

    /** Template for sign-in codes (Authentication category); its variable is {@code otp}. */
    String OTP_TEMPLATE = "otp";

    /** True when this logical template can be sent (credentials, number and an approved template are set up). */
    boolean configured(String template);

    /**
     * @param phone E.164 number, e.g. {@code +919876543210}
     * @param template logical template name, e.g. {@link #OTP_TEMPLATE} or {@code web_order_placed}
     * @param variables values by name; the configured order picks which go into the template
     * @throws WhatsAppException when the provider refuses or cannot be reached
     */
    void send(String phone, String template, Map<String, String> variables);
}

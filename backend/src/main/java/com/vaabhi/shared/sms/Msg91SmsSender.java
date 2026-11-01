package com.vaabhi.shared.sms;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * MSG91 Flow API (v5): one DLT-approved template per message type, variables named as in the template (e.g.
 * {@code ##otp##}). Only delivery happens at MSG91; codes are generated and checked by our backend.
 */
class Msg91SmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsSender.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SmsProperties.Msg91 properties;
    private final HttpClient http;

    Msg91SmsSender(SmsProperties.Msg91 properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public boolean configured() {
        return properties.authKey() != null
                && !properties.authKey().isBlank()
                && !properties.templates().getOrDefault(OTP_TEMPLATE, "").isBlank();
    }

    @Override
    public void send(String phone, String template, Map<String, String> variables) {
        var templateId = properties.templates().get(template);
        if (templateId == null || properties.authKey() == null) {
            throw new SmsException("MSG91 has no template id for '" + template + "'");
        }
        Map<String, String> recipient = new LinkedHashMap<>();
        recipient.put("mobiles", phone.startsWith("+") ? phone.substring(1) : phone);
        recipient.putAll(variables);
        var body = JSON.writeValueAsString(
                Map.of("template_id", templateId, "short_url", "0", "recipients", List.of(recipient)));
        var request = HttpRequest.newBuilder(URI.create(properties.url()))
                .timeout(Duration.ofSeconds(10))
                .header("authkey", properties.authKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SmsException("MSG91 could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SmsException("Interrupted while sending SMS", e);
        }
        if (response.statusCode() / 100 != 2 || !"success".equals(type(response.body()))) {
            // The body names the problem (e.g. DLT template not approved) and holds no secrets.
            log.warn("MSG91 refused [{}]: HTTP {} {}", template, response.statusCode(), response.body());
            throw new SmsException("MSG91 refused the message (HTTP " + response.statusCode() + ")");
        }
    }

    private static String type(String body) {
        try {
            var type = JSON.readTree(body).get("type");
            return type == null ? "" : type.asString();
        } catch (JacksonException e) {
            return "";
        }
    }
}

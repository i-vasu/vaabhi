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
 * MSG91's WhatsApp template API (v5 outbound "bulk" route, one recipient per call). The request follows MSG91's
 * documented sample: the approved template by name and language, body variables as body_1, body_2, … and, for the
 * sign-in code, the code again as the button's value (WhatsApp's copy-code button).
 */
class Msg91WhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(Msg91WhatsAppSender.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WhatsAppProperties properties;
    private final HttpClient http;

    Msg91WhatsAppSender(WhatsAppProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public boolean configured(String template) {
        return notBlank(properties.authKey())
                && notBlank(properties.integratedNumber())
                && properties.templateName(template) != null;
    }

    @Override
    public void send(String phone, String template, Map<String, String> variables) {
        var name = properties.templateName(template);
        if (!configured(template) || name == null) {
            throw new SmsException("WhatsApp is not set up for '" + template + "'");
        }
        var request = HttpRequest.newBuilder(URI.create(properties.url()))
                .timeout(Duration.ofSeconds(10))
                .header("authkey", properties.authKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(body(phone, template, name, variables))))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SmsException("MSG91 could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SmsException("Interrupted while sending WhatsApp", e);
        }
        if (response.statusCode() / 100 != 2 || failed(response.body())) {
            // The body names the problem (e.g. template not approved) and holds no secrets.
            log.warn(
                    "MSG91 WhatsApp refused [{}]: HTTP {} {}",
                    template,
                    response.statusCode(),
                    response.body().replaceAll("[\n\r]", "_"));
            throw new SmsException("MSG91 refused the WhatsApp message (HTTP " + response.statusCode() + ")");
        }
    }

    /** The request body, exactly in the shape of MSG91's sample. */
    Map<String, Object> body(String phone, String template, String name, Map<String, String> variables) {
        Map<String, Object> components = new LinkedHashMap<>();
        var order = properties.params().getOrDefault(template, List.of());
        for (int i = 0; i < order.size(); i++) {
            components.put(
                    "body_" + (i + 1), Map.of("type", "text", "value", variables.getOrDefault(order.get(i), "-")));
        }
        if (SmsSender.OTP_TEMPLATE.equals(template) && variables.containsKey("otp")) {
            components.put("button_1", Map.of("subtype", "url", "type", "text", "value", variables.get("otp")));
        }
        Map<String, Object> templateBody = new LinkedHashMap<>();
        templateBody.put("name", name);
        templateBody.put("language", Map.of("code", properties.languageCode(), "policy", "deterministic"));
        if (notBlank(properties.namespace())) {
            templateBody.put("namespace", properties.namespace());
        }
        templateBody.put(
                "to_and_components",
                List.of(Map.of(
                        "to", List.of(phone.startsWith("+") ? phone.substring(1) : phone), "components", components)));
        return Map.of(
                "integrated_number",
                properties.integratedNumber(),
                "content_type",
                "template",
                "payload",
                Map.of("messaging_product", "whatsapp", "type", "template", "template", templateBody));
    }

    /** MSG91 answers 200 with an error flag for some failures. */
    private static boolean failed(String body) {
        try {
            var json = JSON.readTree(body);
            return json.path("hasError").asBoolean(false)
                    || "error".equalsIgnoreCase(json.path("type").asString(""))
                    || "fail".equalsIgnoreCase(json.path("status").asString(""))
                    || "error".equalsIgnoreCase(json.path("status").asString(""));
        } catch (JacksonException e) {
            return false;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

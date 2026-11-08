package com.vaabhi.shared.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** MSG91's WhatsApp template API against a local stand-in server. */
class Msg91WhatsAppSenderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authKey = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String answer = "{\"status\":\"success\",\"hasError\":false}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authKey.set(exchange.getRequestHeaders().getFirst("authkey"));
            var bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Msg91WhatsAppSender sender(String namespace, String authKey) {
        var properties = new WhatsAppProperties(
                SmsProperties.Provider.MSG91,
                authKey,
                "917569189366",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/bulk/",
                null,
                namespace,
                Map.of("web_order_placed", "vaabhi_order_placed", "otp", "vaabhi_login_code", "web_order_shipped", " "),
                Map.of("web_order_placed", List.of("order", "amount", "payment"), "otp", List.of("otp")));
        return new Msg91WhatsAppSender(properties, HttpClient.newHttpClient());
    }

    @Test
    void sendsTheApprovedTemplateWithItsVariablesInOrder() {
        var whatsApp = sender("", "key-1");
        whatsApp.send(
                "+919876501234",
                "web_order_placed",
                Map.of("order", "WEB-1001", "amount", "24,900", "payment", "Paid online", "courier", "unused"));

        assertThat(authKey.get()).isEqualTo("key-1");
        JsonNode sent = JSON.readTree(body.get());
        assertThat(sent.path("integrated_number").asString()).isEqualTo("917569189366");
        assertThat(sent.path("content_type").asString()).isEqualTo("template");
        var template = sent.path("payload").path("template");
        assertThat(sent.path("payload").path("messaging_product").asString()).isEqualTo("whatsapp");
        assertThat(template.path("name").asString()).isEqualTo("vaabhi_order_placed");
        assertThat(template.path("language").path("code").asString()).isEqualTo("en");
        assertThat(template.path("language").path("policy").asString()).isEqualTo("deterministic");
        assertThat(template.has("namespace")).as("left out when not set").isFalse();
        var recipient = template.path("to_and_components").get(0);
        assertThat(recipient.path("to").get(0).asString()).isEqualTo("919876501234");
        var components = recipient.path("components");
        assertThat(components.path("body_1").path("value").asString()).isEqualTo("WEB-1001");
        assertThat(components.path("body_2").path("value").asString()).isEqualTo("24,900");
        assertThat(components.path("body_3").path("value").asString()).isEqualTo("Paid online");
        assertThat(components.has("body_4")).isFalse();
        assertThat(components.has("button_1")).isFalse();
    }

    @Test
    void sendsTheSignInCodeAlsoAsTheCopyCodeButtonAndAddsTheNamespace() {
        sender("ns-1", "key-1").send("+919876501234", "otp", Map.of("otp", "042137"));
        var template = JSON.readTree(body.get()).path("payload").path("template");
        assertThat(template.path("namespace").asString()).isEqualTo("ns-1");
        var components = template.path("to_and_components").get(0).path("components");
        assertThat(components.path("body_1").path("value").asString()).isEqualTo("042137");
        assertThat(components.path("button_1").path("value").asString()).isEqualTo("042137");
        assertThat(components.path("button_1").path("subtype").asString()).isEqualTo("url");
    }

    @Test
    void reportsRefusalsEvenWhenMsg91AnswersOk() {
        var whatsApp = sender("", "key-1");
        answer = "{\"status\":\"fail\",\"hasError\":true,\"errors\":\"Template not approved\"}";
        assertThatThrownBy(() -> whatsApp.send("+919876501234", "web_order_placed", Map.of("order", "WEB-1")))
                .isInstanceOf(SmsException.class)
                .hasMessageContaining("refused");
        status = 401;
        answer = "{\"message\":\"Unauthorized\"}";
        assertThatThrownBy(() -> whatsApp.send("+919876501234", "web_order_placed", Map.of("order", "WEB-1")))
                .isInstanceOf(SmsException.class);
    }

    @Test
    void sendsOnlyTemplatesThatAreSetUp() {
        var whatsApp = sender("", "key-1");
        assertThat(whatsApp.configured("web_order_placed")).isTrue();
        assertThat(whatsApp.configured("web_order_shipped")).as("blank name").isFalse();
        assertThat(whatsApp.configured("web_order_cancelled")).isFalse();
        assertThat(sender("", "").configured("web_order_placed"))
                .as("no auth key")
                .isFalse();
        assertThatThrownBy(() -> whatsApp.send("+919876501234", "web_order_cancelled", Map.of()))
                .isInstanceOf(SmsException.class);
        assertThat(body.get()).isNull();
    }

    @Test
    void logProviderOnlyLogsTemplatesThatAreSetUp() {
        var properties = new WhatsAppProperties(
                null, null, null, null, null, null, Map.of("web_order_placed", "vaabhi_order_placed"), null);
        var log = new LogWhatsAppSender(properties);
        assertThat(log.configured("web_order_placed")).isTrue();
        assertThat(log.configured("otp")).isFalse();
        log.send("+919876501234", "web_order_placed", Map.of("order", "WEB-1"));
        assertThat(properties.url()).endsWith("/whatsapp-outbound-message/bulk/");
    }
}

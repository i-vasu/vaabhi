package com.vaabhi.shared.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Against a local stand-in for MSG91's Flow API. */
class Msg91SmsSenderTest {

    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authKey = new AtomicReference<>();
    private HttpServer server;

    private Msg91SmsSender sender(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v5/flow", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authKey.set(exchange.getRequestHeaders().getFirst("authkey"));
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v5/flow";
        return new Msg91SmsSender(
                new SmsProperties.Msg91("key-123", url, Map.of("otp", "tmpl-otp")), HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheTemplateWithTheNumberWithoutPlus() throws Exception {
        var sender = sender(200, "{\"type\":\"success\",\"message\":\"3463\"}");

        sender.send("+919876543210", SmsSender.OTP_TEMPLATE, Map.of("otp", "042137"));

        assertThat(authKey.get()).isEqualTo("key-123");
        assertThat(body.get())
                .contains("\"template_id\":\"tmpl-otp\"")
                .contains("\"mobiles\":\"919876543210\"")
                .contains("\"otp\":\"042137\"");
    }

    @Test
    void failsWhenMsg91Refuses() throws Exception {
        var sender = sender(200, "{\"type\":\"error\",\"message\":\"Template not approved\"}");
        assertThatThrownBy(() -> sender.send("+919876543210", "otp", Map.of("otp", "1")))
                .isInstanceOf(SmsException.class);

        stop();
        var down = sender(500, "oops");
        assertThatThrownBy(() -> down.send("+919876543210", "otp", Map.of("otp", "1")))
                .isInstanceOf(SmsException.class);
    }

    @Test
    void isOnlyConfiguredWithKeyAndOtpTemplate() {
        var http = HttpClient.newHttpClient();
        assertThat(new Msg91SmsSender(new SmsProperties.Msg91("k", null, Map.of("otp", "t")), http).configured())
                .isTrue();
        assertThat(new Msg91SmsSender(new SmsProperties.Msg91("", null, Map.of("otp", "t")), http).configured())
                .isFalse();
        assertThat(new Msg91SmsSender(new SmsProperties.Msg91("k", null, Map.of("otp", "")), http).configured())
                .isFalse();
        assertThatThrownBy(() -> new Msg91SmsSender(new SmsProperties.Msg91("k", null, Map.of()), http)
                        .send("+919876543210", "order-shipped", Map.of()))
                .isInstanceOf(SmsException.class);
    }

    @Test
    void theLogProviderIsRefusedInProduction() {
        var production = new MockEnvironment();
        production.setActiveProfiles("prod");
        var properties = new SmsProperties(SmsProperties.Provider.LOG, null);

        assertThatThrownBy(() -> new SmsConfig().smsSender(properties, production))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new SmsConfig().smsSender(properties, new MockEnvironment())).isInstanceOf(LogSmsSender.class);
        assertThat(new SmsConfig().smsSender(new SmsProperties(SmsProperties.Provider.MSG91, null), production))
                .isInstanceOf(Msg91SmsSender.class);
    }
}

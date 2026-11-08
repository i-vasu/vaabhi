package com.vaabhi.shared.sms;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties({SmsProperties.class, WhatsAppProperties.class})
class SmsConfig {

    @Bean
    SmsSender smsSender(SmsProperties properties, Environment environment) {
        return switch (properties.provider()) {
            case MSG91 ->
                new Msg91SmsSender(
                        properties.msg91(),
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build());
            case LOG -> {
                // The log provider writes codes to the log (exported to Grafana in production).
                if (environment.matchesProfiles("prod")) {
                    throw new IllegalStateException("vaabhi.sms.provider=log is not allowed with the prod profile");
                }
                yield new LogSmsSender();
            }
        };
    }

    @Bean
    WhatsAppSender whatsAppSender(WhatsAppProperties properties, Environment environment) {
        return switch (properties.provider()) {
            case MSG91 ->
                new Msg91WhatsAppSender(
                        properties,
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build());
            case LOG -> {
                // Like SMS: the log provider would write sign-in codes to the log.
                if (environment.matchesProfiles("prod")) {
                    throw new IllegalStateException(
                            "vaabhi.whatsapp.provider=log is not allowed with the prod profile");
                }
                yield new LogWhatsAppSender(properties);
            }
        };
    }
}

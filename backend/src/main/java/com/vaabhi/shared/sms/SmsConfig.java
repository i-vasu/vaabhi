package com.vaabhi.shared.sms;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
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
}

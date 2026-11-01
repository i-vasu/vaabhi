package com.vaabhi.customer;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuthProperties.class)
class CustomerConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Nightly: drops used/expired codes and refresh tokens past their expiry. */
    @Component
    static class AuthCleanup {

        private static final Logger log = LoggerFactory.getLogger(AuthCleanup.class);

        private final OtpStore otps;
        private final RefreshTokenStore refreshTokens;
        private final Clock clock;

        AuthCleanup(OtpStore otps, RefreshTokenStore refreshTokens, Clock clock) {
            this.otps = otps;
            this.refreshTokens = refreshTokens;
            this.clock = clock;
        }

        @Scheduled(cron = "0 17 3 * * *", zone = "Asia/Kolkata")
        @Transactional
        public void purge() {
            var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            var codes = otps.deleteOlderThan(now.minusDays(1));
            var tokens = refreshTokens.deleteExpired(now);
            log.info("Auth cleanup: removed {} codes and {} expired sessions", codes, tokens);
        }
    }
}

package com.vaabhi.shared.database;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ProfileGuardTest {

    private final ProfileGuard guard = new ProfileGuard();

    @Test
    void refusesDevInProduction() {
        var environment = new MockEnvironment().withProperty("DEPLOY_ENV", "production");
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> run(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev profile");
    }

    @Test
    void refusesDevAsTheDefaultInProduction() {
        var environment = new MockEnvironment().withProperty("DEPLOY_ENV", "production");
        environment.setDefaultProfiles("dev");

        assertThatThrownBy(() -> run(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=prod");
    }

    @Test
    void refusesDevTogetherWithProd() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("dev", "prod");

        assertThatThrownBy(() -> run(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("together");
    }

    @Test
    void prodNamesMissingVariables() {
        var environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://h/db")
                .withProperty("JWT_SECRET", " ");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> run(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[JWT_SECRET, CORS_ALLOWED_ORIGINS]");
    }

    @Test
    void allowsDevLocallyAndCompleteProdInProduction() {
        var local = new MockEnvironment();
        local.setDefaultProfiles("dev");
        var production = new MockEnvironment()
                .withProperty("DEPLOY_ENV", "production")
                .withProperty("DATABASE_URL", "postgresql://h/db")
                .withProperty("JWT_SECRET", "s3cret")
                .withProperty("CORS_ALLOWED_ORIGINS", "https://vaabhi.com");
        production.setActiveProfiles("prod");
        production.setDefaultProfiles("dev");

        assertThatCode(() -> run(local)).doesNotThrowAnyException();
        assertThatCode(() -> run(production)).doesNotThrowAnyException();
    }

    private void run(MockEnvironment environment) {
        guard.postProcessEnvironment(environment, new SpringApplication());
    }
}

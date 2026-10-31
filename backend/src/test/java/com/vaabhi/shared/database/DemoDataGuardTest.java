package com.vaabhi.shared.database;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DemoDataGuardTest {

    private final DemoDataGuard guard = new DemoDataGuard();

    @Test
    void refusesDemoDataInProduction() {
        var environment = new MockEnvironment().withProperty("DEPLOY_ENV", "production");
        environment.setActiveProfiles("demo");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo profile");
    }

    @Test
    void allowsDemoDataElsewhereAndProductionWithoutIt() {
        var local = new MockEnvironment();
        local.setActiveProfiles("demo");
        var production = new MockEnvironment().withProperty("DEPLOY_ENV", "production");

        assertThatCode(() -> guard.postProcessEnvironment(local, new SpringApplication()))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.postProcessEnvironment(production, new SpringApplication()))
                .doesNotThrowAnyException();
    }
}

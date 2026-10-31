package com.vaabhi.shared.database;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuses to start when the {@code demo} profile (which loads demo products into the database) is active in
 * production. Runs before Flyway, so nothing is ever written.
 */
class DemoDataGuard implements EnvironmentPostProcessor {

    static final String DEMO_PROFILE = "demo";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var demo = Arrays.asList(environment.getActiveProfiles()).contains(DEMO_PROFILE);
        var deployEnv = environment.getProperty("DEPLOY_ENV", "local").toLowerCase(Locale.ROOT);
        if (demo && deployEnv.startsWith("prod")) {
            throw new IllegalStateException(
                    "The demo profile loads demo products and must never run in production (DEPLOY_ENV=" + deployEnv
                            + ")");
        }
    }
}

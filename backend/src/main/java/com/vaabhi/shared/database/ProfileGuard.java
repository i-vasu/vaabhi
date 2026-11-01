package com.vaabhi.shared.database;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Checks the {@code dev} / {@code prod} profile setup before anything (including Flyway) runs:
 *
 * <ul>
 *   <li>{@code dev} loads demo products, so it is refused with {@code DEPLOY_ENV=production} or together with
 *       {@code prod}. {@code dev} is the default profile, so this also catches a production deploy that forgot
 *       {@code SPRING_PROFILES_ACTIVE=prod}.
 *   <li>{@code prod} has no fallbacks, so it names every missing required variable instead of failing later with
 *       an obscure datasource error.
 * </ul>
 */
class ProfileGuard implements EnvironmentPostProcessor {

    static final String DEV_PROFILE = "dev";
    static final String PROD_PROFILE = "prod";
    static final List<String> PROD_REQUIRED = List.of("DATABASE_URL", "JWT_SECRET", "CORS_ALLOWED_ORIGINS");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // matchesProfiles also sees default profiles when none is active.
        var dev = environment.matchesProfiles(DEV_PROFILE);
        var prod = environment.matchesProfiles(PROD_PROFILE);
        if (dev && prod) {
            throw new IllegalStateException("The dev and prod profiles cannot be active together");
        }
        if (dev) {
            var deployEnv = environment.getProperty("DEPLOY_ENV", "local").toLowerCase(Locale.ROOT);
            if (deployEnv.startsWith("prod")) {
                throw new IllegalStateException("The dev profile loads demo products and must never run in production"
                        + " (DEPLOY_ENV=" + deployEnv + "). Set SPRING_PROFILES_ACTIVE=prod.");
            }
        }
        if (prod) {
            var missing = PROD_REQUIRED.stream()
                    .filter(name -> !environment.containsProperty(name)
                            || environment.getProperty(name, "").isBlank())
                    .toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("The prod profile needs these environment variables: " + missing);
            }
        }
    }
}

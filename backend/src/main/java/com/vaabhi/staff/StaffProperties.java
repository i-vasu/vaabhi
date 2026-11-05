package com.vaabhi.staff;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Staff sign-in settings under {@code vaabhi.staff}.
 *
 * @param tokenTtl how long a dashboard sign-in lasts
 * @param maxFailedLogins wrong passwords in a row before the account is locked
 * @param lockout how long a locked account stays locked
 * @param owner the first owner account, created on startup only while no staff account exists
 */
@ConfigurationProperties("vaabhi.staff")
record StaffProperties(
        @DefaultValue("12h") Duration tokenTtl,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockout,
        @Nullable Owner owner) {

    /** Unset (blank email or password) means no owner is created. */
    record Owner(
            @DefaultValue("Owner") String name,
            @Nullable String email,
            @Nullable String password) {

        boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}

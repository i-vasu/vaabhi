package com.vaabhi.staff;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first owner account from {@code vaabhi.staff.owner.*} (env {@code STAFF_OWNER_EMAIL},
 * {@code STAFF_OWNER_PASSWORD}) while the shop has no staff yet. Once any account exists this does nothing, so
 * changing the variables later never resets a password.
 */
@Component
class StaffBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffBootstrap.class);

    private final StaffAccountStore store;
    private final PasswordEncoder passwordEncoder;
    private final StaffProperties properties;

    StaffBootstrap(StaffAccountStore store, PasswordEncoder passwordEncoder, StaffProperties properties) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var owner = properties.owner();
        if (owner == null || !owner.isConfigured() || store.count() > 0) {
            return;
        }
        store.create(
                owner.name(),
                Objects.requireNonNull(owner.email()),
                null,
                StaffRole.OWNER,
                passwordEncoder.encode(Objects.requireNonNull(owner.password())));
        log.info("Created the first owner account for the admin dashboard");
    }
}

package com.vaabhi;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(VaabhiApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }
}

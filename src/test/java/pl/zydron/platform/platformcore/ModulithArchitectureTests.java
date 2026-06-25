package pl.zydron.platform.platformcore;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTests {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(PlatformCoreApplication.class).verify();
    }
}

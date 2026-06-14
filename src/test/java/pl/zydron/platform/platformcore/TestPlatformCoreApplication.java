package pl.zydron.platform.platformcore;

import org.springframework.boot.SpringApplication;

public class TestPlatformCoreApplication {

    public static void main(String[] args) {
        SpringApplication.from(PlatformCoreApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

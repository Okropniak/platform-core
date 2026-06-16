package pl.zydron.platform.platformcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class PlatformCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformCoreApplication.class, args);
    }

}

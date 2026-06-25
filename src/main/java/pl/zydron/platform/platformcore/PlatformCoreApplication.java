package pl.zydron.platform.platformcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Główny punkt startowy aplikacji Platform Core.
 *
 * <p>Spring Boot wyszukuje stąd komponenty we wszystkich podpakietach i
 * uruchamia jeden proces zawierający wszystkie moduły biznesowe. Aplikacja
 * jest więc modularnym monolitem, a nie zestawem osobno wdrażanych
 * mikroserwisów.</p>
 */
@SpringBootApplication
public class PlatformCoreApplication {

    /**
     * Buduje kontekst Springa i uruchamia serwer HTTP.
     *
     * @param args argumenty wiersza poleceń przekazane do Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformCoreApplication.class, args);
    }

}

package pl.zydron.platform.platformcore.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
/**
 * Konfiguruje wykonywanie metod oznaczonych adnotacją {@code @Async}.
 *
 * <p>Oddzielna, ograniczona pula wątków jest używana przez zapis audytu.
 * Ograniczenie liczby wątków i kolejki chroni aplikację przed utworzeniem
 * niekontrolowanej liczby zadań podczas dużego obciążenia.</p>
 */
public class AsyncConfiguration implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

    @Bean(name = "auditTaskExecutor")
    /**
     * Tworzy wykonawcę zadań audytowych.
     *
     * <p>Gdy pula i kolejka są pełne, {@link ThreadPoolExecutor.CallerRunsPolicy}
     * wykonuje zadanie w wątku wywołującym. Spowalnia to nadawcę zamiast
     * bezgłośnie zgubić zdarzenie lub tworzyć kolejne wątki.</p>
     *
     * @return ograniczona pula wątków używana przez {@code AuditService}
     */
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("audit-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Override
    /**
     * Zwraca handler błędów metod asynchronicznych zwracających {@code void}.
     *
     * @return handler zapisujący wyjątek w logach
     */
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncExceptionHandler();
    }

    private static class LoggingAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

        @Override
        /**
         * Loguje błąd, którego nie można przekazać do wywołującego, ponieważ
         * metoda asynchroniczna zakończyła się już poza jego wątkiem.
         */
        public void handleUncaughtException(Throwable exception, Method method, Object... params) {
            log.warn("Async method {} failed.", method.getName(), exception);
        }
    }
}

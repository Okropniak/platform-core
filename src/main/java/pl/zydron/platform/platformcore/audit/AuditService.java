package pl.zydron.platform.platformcore.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Zapisuje ważne zdarzenia biznesowe do append-only logu audytowego.
 *
 * <p>Metoda działa w osobnym wątku i osobnej transakcji. Wywołujący nie czeka
 * na zapis, a błąd audytu nie wycofuje operacji biznesowej. Ceną jest
 * możliwość utraty oczekującego wpisu przy nagłym zatrzymaniu procesu.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Async("auditTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * Tworzy pojedynczy wpis audytu.
     *
     * @param organizationId organizacja związana ze zdarzeniem; może być null
     * @param userId użytkownik wykonujący operację
     * @param productCode produkt, którego dotyczy zdarzenie
     * @param eventType stabilny kod rodzaju zdarzenia
     * @param entityType rodzaj zmienianego obiektu
     * @param entityId tekstowy identyfikator obiektu
     * @param metadata dodatkowe, niewrażliwe dane diagnostyczne
     */
    public void record(
            UUID organizationId,
            UUID userId,
            String productCode,
            String eventType,
            String entityType,
            String entityId,
            Map<String, Object> metadata
    ) {
        // Map.copyOf tworzy niemodyfikowalną kopię, aby późniejsza zmiana mapy
        // przez wywołującego nie zmieniła danych oczekującego zadania async.
        auditEventRepository.save(AuditEventEntity.builder()
                .organizationId(organizationId)
                .userId(userId)
                .productCode(productCode)
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadata == null ? Map.of() : Map.copyOf(metadata))
                .build());
    }
}

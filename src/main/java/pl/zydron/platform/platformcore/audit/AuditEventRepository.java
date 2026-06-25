package pl.zydron.platform.platformcore.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repozytorium zapisu i administracyjnego odczytu zdarzeń audytowych.
 */
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
}

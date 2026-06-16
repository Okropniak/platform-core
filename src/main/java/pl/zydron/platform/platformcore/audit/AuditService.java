package pl.zydron.platform.platformcore.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Async("auditTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID organizationId,
            UUID userId,
            String productCode,
            String eventType,
            String entityType,
            String entityId,
            Map<String, Object> metadata
    ) {
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

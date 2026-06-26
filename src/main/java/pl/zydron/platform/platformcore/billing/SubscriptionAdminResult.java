package pl.zydron.platform.platformcore.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Publiczny DTO wynikowy dla administracyjnych operacji na subskrypcjach.
 *
 * <p>Zastępuje bezpośredni zwrot {@code SubscriptionEntity} z
 * {@link AdminBillingPort}, dzięki czemu moduł admin nie zależy od
 * wewnętrznego modelu JPA modułu billing.</p>
 */
public record SubscriptionAdminResult(
        UUID id,
        UUID organizationId,
        String productCode,
        String planCode,
        String status,
        String provider,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        OffsetDateTime cancelledAt
) {}

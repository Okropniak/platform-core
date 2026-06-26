package pl.zydron.platform.platformcore.billing;

import java.util.UUID;

/**
 * Publiczny kontrakt modułu billing dla operacji administracyjnych.
 *
 * <p>Moduł admin zależy od tego interfejsu, nie od klasy {@link BillingService}.
 * Metody zwracają {@link SubscriptionAdminResult} — publiczny DTO bez encji JPA,
 * co pozwoli w przyszłości przenieść wewnętrzny model billing do subpakietu
 * {@code internal} bez zmian w konsumentach.</p>
 */
public interface AdminBillingPort {

    /**
     * Aktywuje ręcznie wskazany plan dla dowolnej istniejącej organizacji.
     */
    SubscriptionAdminResult createManualSubscriptionAsAdmin(
            UUID organizationId, UUID adminUserId, String productCode, String planCode);

    /**
     * Zmienia plan lub status istniejącej bądź tworzonej subskrypcji.
     */
    SubscriptionAdminResult changeSubscriptionAsAdmin(
            UUID organizationId, UUID adminUserId, String productCode, String planCode, String newStatus);
}

package pl.zydron.platform.platformcore.tenants;

import java.util.UUID;

/**
 * Publiczny kontrakt modułu tenants dla operacji cross-module.
 *
 * <p>Inne moduły (billing, usage, entitlements, products) zależą od tego
 * interfejsu, nie od klasy {@link TenantService}. Dzięki temu refaktor
 * implementacji nie wymaga zmian w konsumentach, a przyszłe przeniesienie
 * {@link TenantService} do subpakietu {@code internal} wymusi granice przez
 * {@code ApplicationModules.verify()}.</p>
 *
 * <p>Wszystkie metody są strażnikami — przy braku uprawnień rzucają
 * {@code PlatformAccessDeniedException} lub {@code BadRequestException}.
 * Brak wyjątku oznacza pozytywny wynik weryfikacji.</p>
 */
public interface TenantAccessPort {

    /**
     * Weryfikuje, że użytkownik ma aktywną rolę owner lub admin.
     *
     * @throws pl.zydron.platform.platformcore.common.PlatformAccessDeniedException gdy nie jest managerem
     */
    void requireManager(UUID organizationId, UUID userId);

    /**
     * Weryfikuje, że użytkownik jest aktywnym członkiem organizacji.
     *
     * @throws pl.zydron.platform.platformcore.common.PlatformAccessDeniedException gdy nie jest aktywnym członkiem
     */
    void requireActiveMember(UUID organizationId, UUID userId);

    /**
     * Weryfikuje, że organizacja istnieje.
     *
     * @throws pl.zydron.platform.platformcore.common.BadRequestException gdy organizacja nie istnieje
     */
    void requireOrganizationExists(UUID organizationId);
}

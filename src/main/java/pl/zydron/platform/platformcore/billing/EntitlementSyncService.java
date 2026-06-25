package pl.zydron.platform.platformcore.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.entitlements.EntitlementService;

import java.util.UUID;

/**
 * Adapter pomiędzy modułem billing i modułem entitlements.
 *
 * <p>Billing nie modyfikuje tabel entitlementów bezpośrednio. Deleguje zmianę
 * do publicznego serwisu modułu odpowiedzialnego za te dane.</p>
 */
@Service
@RequiredArgsConstructor
public class EntitlementSyncService {

    private final EntitlementService entitlementService;

    @Transactional
    /**
     * Odtwarza entitlementy organizacji na podstawie szablonu planu.
     */
    public void syncFromPlan(UUID organizationId, String productCode, String planCode) {
        entitlementService.syncEntitlementsFromPlan(organizationId, productCode, planCode);
    }

    @Transactional
    /**
     * Wyłącza entitlementy pochodzące z planu, zachowując ręczne override'y.
     */
    public void disablePlanEntitlements(UUID organizationId, String productCode) {
        entitlementService.disablePlanEntitlements(organizationId, productCode);
    }
}

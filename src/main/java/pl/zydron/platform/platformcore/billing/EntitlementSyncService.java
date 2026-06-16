package pl.zydron.platform.platformcore.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.entitlements.EntitlementService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntitlementSyncService {

    private final EntitlementService entitlementService;

    @Transactional
    public void syncFromPlan(UUID organizationId, String productCode, String planCode) {
        entitlementService.syncEntitlementsFromPlan(organizationId, productCode, planCode);
    }

    @Transactional
    public void disablePlanEntitlements(UUID organizationId, String productCode) {
        entitlementService.disablePlanEntitlements(organizationId, productCode);
    }
}

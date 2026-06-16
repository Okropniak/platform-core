package pl.zydron.platform.platformcore.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final String FREE_PLAN = "free";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantService tenantService;
    private final EntitlementSyncService entitlementSyncService;
    private final AuditService auditService;

    @Transactional
    public SubscriptionEntity createManualSubscription(
            UUID organizationId,
            UUID requestingUserId,
            String productCode,
            String planCode
    ) {
        tenantService.requireManager(organizationId, requestingUserId);
        PlanEntity plan = requireActivePlan(productCode, planCode);
        OffsetDateTime now = OffsetDateTime.now();

        SubscriptionEntity subscription = subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, productCode)
                .orElseGet(() -> newSubscription(organizationId, plan, now));
        applyStatus(subscription, plan, "active", "manual", now);

        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        entitlementSyncService.syncFromPlan(organizationId, productCode, planCode);
        auditSubscriptionChanged(
                organizationId,
                requestingUserId,
                productCode,
                saved,
                "subscription_created",
                Map.of("planCode", planCode, "status", saved.getStatus(), "provider", saved.getProvider())
        );
        return saved;
    }

    @Transactional
    public SubscriptionEntity cancelSubscription(UUID organizationId, UUID requestingUserId, String productCode) {
        tenantService.requireManager(organizationId, requestingUserId);
        OffsetDateTime now = OffsetDateTime.now();
        SubscriptionEntity subscription = subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, productCode)
                .orElseThrow(() -> new BadRequestException("Subscription does not exist."));

        subscription.setStatus("cancelled");
        subscription.setCancelledAt(now);
        subscription.setUpdatedAt(now);

        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        downgradeToFreePlanIfAvailable(organizationId, productCode);
        auditSubscriptionChanged(
                organizationId,
                requestingUserId,
                productCode,
                saved,
                "subscription_cancelled",
                Map.of("planCode", saved.getPlanCode(), "status", saved.getStatus())
        );
        return saved;
    }

    @Transactional
    public SubscriptionEntity onSubscriptionChanged(
            UUID organizationId,
            UUID requestingUserId,
            String productCode,
            String planCode,
            String newStatus
    ) {
        tenantService.requireManager(organizationId, requestingUserId);
        PlanEntity plan = requireActivePlan(productCode, planCode);
        OffsetDateTime now = OffsetDateTime.now();

        SubscriptionEntity subscription = subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, productCode)
                .orElseGet(() -> newSubscription(organizationId, plan, now));
        applyStatus(subscription, plan, newStatus, null, now);

        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        if ("active".equals(newStatus) || "manual".equals(newStatus)) {
            entitlementSyncService.syncFromPlan(organizationId, productCode, planCode);
        } else if ("cancelled".equals(newStatus) || "expired".equals(newStatus)) {
            downgradeToFreePlanIfAvailable(organizationId, productCode);
        }
        auditSubscriptionChanged(
                organizationId,
                requestingUserId,
                productCode,
                saved,
                "subscription_changed",
                Map.of("planCode", planCode, "status", saved.getStatus(), "provider", saved.getProvider())
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionEntity> getSubscriptions(UUID organizationId, UUID requestingUserId) {
        tenantService.requireActiveMember(organizationId, requestingUserId);
        return subscriptionRepository.findByOrganizationIdOrderByProductCodeAsc(organizationId);
    }

    private PlanEntity requireActivePlan(String productCode, String planCode) {
        return planRepository.findByProductCodeAndPlanCodeAndActiveTrue(productCode, planCode)
                .orElseThrow(() -> new BadRequestException("Plan does not exist or is not active."));
    }

    private void applyStatus(
            SubscriptionEntity subscription,
            PlanEntity plan,
            String status,
            String provider,
            OffsetDateTime now
    ) {
        subscription.setPlanCode(plan.getPlanCode());
        subscription.setStatus(status);
        if (provider != null || subscription.getProvider() == null) {
            subscription.setProvider(provider == null ? "manual" : provider);
        }
        if ("active".equals(status) || "manual".equals(status) || "trial".equals(status)) {
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(periodEnd(plan, now));
            subscription.setCancelledAt(null);
        }
        if ("cancelled".equals(status) || "expired".equals(status)) {
            subscription.setCancelledAt(now);
        }
        subscription.setUpdatedAt(now);
    }

    private SubscriptionEntity newSubscription(UUID organizationId, PlanEntity plan, OffsetDateTime now) {
        return SubscriptionEntity.builder()
                .organizationId(organizationId)
                .productCode(plan.getProductCode())
                .planCode(plan.getPlanCode())
                .status("active")
                .provider("manual")
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd(plan, now))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private OffsetDateTime periodEnd(PlanEntity plan, OffsetDateTime start) {
        return switch (plan.getBillingPeriod()) {
            case "yearly" -> start.plusYears(1);
            case "monthly" -> start.plusMonths(1);
            case "one_time", "manual" -> null;
            default -> null;
        };
    }

    private void downgradeToFreePlanIfAvailable(UUID organizationId, String productCode) {
        if (planRepository.findByProductCodeAndPlanCodeAndActiveTrue(productCode, FREE_PLAN).isPresent()) {
            entitlementSyncService.syncFromPlan(organizationId, productCode, FREE_PLAN);
        } else {
            entitlementSyncService.disablePlanEntitlements(organizationId, productCode);
        }
    }

    private void auditSubscriptionChanged(
            UUID organizationId,
            UUID userId,
            String productCode,
            SubscriptionEntity subscription,
            String eventType,
            Map<String, Object> metadata
    ) {
        auditService.record(
                organizationId,
                userId,
                productCode,
                eventType,
                "subscription",
                subscription.getId() == null ? productCode : subscription.getId().toString(),
                metadata
        );
    }
}

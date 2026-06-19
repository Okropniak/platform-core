package pl.zydron.platform.platformcore.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.billing.EntitlementSyncService;
import pl.zydron.platform.platformcore.billing.PlanEntity;
import pl.zydron.platform.platformcore.billing.PlanRepository;
import pl.zydron.platform.platformcore.billing.SubscriptionEntity;
import pl.zydron.platform.platformcore.billing.SubscriptionRepository;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.OrganizationRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminBillingService {

    private static final String FREE_PLAN = "free";

    private final OrganizationRepository organizationRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementSyncService entitlementSyncService;
    private final AuditService auditService;

    @Transactional
    public SubscriptionEntity activateManualSubscription(
            UUID organizationId,
            UUID adminUserId,
            String productCode,
            String planCode
    ) {
        requireOrganization(organizationId);
        PlanEntity plan = requireActivePlan(productCode, planCode);
        OffsetDateTime now = OffsetDateTime.now();
        SubscriptionEntity subscription = subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, productCode)
                .orElseGet(() -> newSubscription(organizationId, plan, now));

        applyStatus(subscription, plan, "active", "manual", now);
        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        entitlementSyncService.syncFromPlan(organizationId, productCode, planCode);
        auditSubscriptionChanged(organizationId, adminUserId, productCode, saved, "admin_subscription_created");
        return saved;
    }

    @Transactional
    public SubscriptionEntity changeSubscription(
            UUID organizationId,
            UUID adminUserId,
            String productCode,
            String planCode,
            String status
    ) {
        requireOrganization(organizationId);
        PlanEntity plan = requireActivePlan(productCode, planCode);
        OffsetDateTime now = OffsetDateTime.now();
        SubscriptionEntity subscription = subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, productCode)
                .orElseGet(() -> newSubscription(organizationId, plan, now));

        applyStatus(subscription, plan, status, null, now);
        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        if ("active".equals(status) || "manual".equals(status) || "trial".equals(status)) {
            entitlementSyncService.syncFromPlan(organizationId, productCode, planCode);
        } else if ("cancelled".equals(status) || "expired".equals(status)) {
            downgradeToFreePlanIfAvailable(organizationId, productCode);
        }
        auditSubscriptionChanged(organizationId, adminUserId, productCode, saved, "admin_subscription_changed");
        return saved;
    }

    private PlanEntity requireActivePlan(String productCode, String planCode) {
        return planRepository.findByProductCodeAndPlanCodeAndActiveTrue(productCode, planCode)
                .orElseThrow(() -> new BadRequestException("Plan does not exist or is not active."));
    }

    private void requireOrganization(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new BadRequestException("Organization does not exist.");
        }
    }

    private void applyStatus(
            SubscriptionEntity subscription,
            PlanEntity plan,
            String status,
            String provider,
            OffsetDateTime now
    ) {
        subscription.setProductCode(plan.getProductCode());
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
            UUID adminUserId,
            String productCode,
            SubscriptionEntity subscription,
            String eventType
    ) {
        auditService.record(
                organizationId,
                adminUserId,
                productCode,
                eventType,
                "subscription",
                subscription.getId() == null ? productCode : subscription.getId().toString(),
                Map.of("planCode", subscription.getPlanCode(), "status", subscription.getStatus(), "provider", subscription.getProvider())
        );
    }
}

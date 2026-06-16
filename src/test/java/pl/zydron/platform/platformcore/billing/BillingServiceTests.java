package pl.zydron.platform.platformcore.billing;

import org.junit.jupiter.api.Test;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingServiceTests {

    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final TenantService tenantService = mock(TenantService.class);
    private final EntitlementSyncService entitlementSyncService = mock(EntitlementSyncService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final BillingService billingService = new BillingService(
            planRepository,
            subscriptionRepository,
            tenantService,
            entitlementSyncService,
            auditService
    );

    @Test
    void createManualSubscriptionCreatesActiveSubscriptionAndSyncsEntitlements() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanEntity plan = searchPlan("pro");

        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "pro"))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, "search_saas"))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity subscription = billingService.createManualSubscription(
                organizationId,
                userId,
                "search_saas",
                "pro"
        );

        assertThat(subscription.getStatus()).isEqualTo("active");
        assertThat(subscription.getProvider()).isEqualTo("manual");
        assertThat(subscription.getCurrentPeriodEnd()).isAfter(subscription.getCurrentPeriodStart());
        verify(tenantService).requireManager(organizationId, userId);
        verify(entitlementSyncService).syncFromPlan(organizationId, "search_saas", "pro");
    }

    @Test
    void cancelSubscriptionDowngradesEntitlementsToFreePlanWhenAvailable() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SubscriptionEntity existing = SubscriptionEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .productCode("search_saas")
                .planCode("pro")
                .status("active")
                .provider("manual")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, "search_saas"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "free"))
                .thenReturn(Optional.of(searchPlan("free")));

        SubscriptionEntity cancelled = billingService.cancelSubscription(organizationId, userId, "search_saas");

        assertThat(cancelled.getStatus()).isEqualTo("cancelled");
        assertThat(cancelled.getCancelledAt()).isNotNull();
        verify(entitlementSyncService).syncFromPlan(organizationId, "search_saas", "free");
    }

    @Test
    void cancelSubscriptionDisablesPlanEntitlementsWhenNoFreePlanExists() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SubscriptionEntity existing = SubscriptionEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .productCode("enterprise_saas")
                .planCode("pro")
                .status("active")
                .provider("manual")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, "enterprise_saas"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("enterprise_saas", "free"))
                .thenReturn(Optional.empty());

        billingService.cancelSubscription(organizationId, userId, "enterprise_saas");

        verify(entitlementSyncService).disablePlanEntitlements(organizationId, "enterprise_saas");
        verify(entitlementSyncService, never()).syncFromPlan(organizationId, "enterprise_saas", "free");
    }

    @Test
    void subscriptionChangeCreatesTerminalSubscriptionWithCancelledAt() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanEntity plan = searchPlan("pro");

        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "pro"))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, "search_saas"))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "free"))
                .thenReturn(Optional.of(searchPlan("free")));

        SubscriptionEntity cancelled = billingService.onSubscriptionChanged(
                organizationId,
                userId,
                "search_saas",
                "pro",
                "cancelled"
        );

        assertThat(cancelled.getStatus()).isEqualTo("cancelled");
        assertThat(cancelled.getCancelledAt()).isNotNull();
        verify(entitlementSyncService).syncFromPlan(organizationId, "search_saas", "free");
    }

    @Test
    void expiredSubscriptionRecordsTerminalTimestamp() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanEntity plan = searchPlan("pro");
        SubscriptionEntity existing = SubscriptionEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .productCode("search_saas")
                .planCode("pro")
                .status("active")
                .provider("manual")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "pro"))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, "search_saas"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "free"))
                .thenReturn(Optional.of(searchPlan("free")));

        SubscriptionEntity expired = billingService.onSubscriptionChanged(
                organizationId,
                userId,
                "search_saas",
                "pro",
                "expired"
        );

        assertThat(expired.getStatus()).isEqualTo("expired");
        assertThat(expired.getCancelledAt()).isNotNull();
    }

    @Test
    void rejectsUnknownPlanBeforeWritingSubscription() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "enterprise"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.createManualSubscription(
                organizationId,
                userId,
                "search_saas",
                "enterprise"
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Plan does not exist");

        verify(subscriptionRepository, never()).save(any());
    }

    private PlanEntity searchPlan(String planCode) {
        return PlanEntity.builder()
                .id(UUID.randomUUID())
                .productCode("search_saas")
                .planCode(planCode)
                .name(planCode)
                .price("pro".equals(planCode) ? BigDecimal.valueOf(29) : BigDecimal.ZERO)
                .currency("USD")
                .billingPeriod("monthly")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}

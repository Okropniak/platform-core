package pl.zydron.platform.platformcore.admin;

import org.junit.jupiter.api.Test;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.billing.EntitlementSyncService;
import pl.zydron.platform.platformcore.billing.PlanEntity;
import pl.zydron.platform.platformcore.billing.PlanRepository;
import pl.zydron.platform.platformcore.billing.SubscriptionEntity;
import pl.zydron.platform.platformcore.billing.SubscriptionRepository;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.OrganizationRepository;

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

class AdminBillingServiceTests {

    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final EntitlementSyncService entitlementSyncService = mock(EntitlementSyncService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminBillingService adminBillingService = new AdminBillingService(
            organizationRepository,
            planRepository,
            subscriptionRepository,
            entitlementSyncService,
            auditService
    );

    @Test
    void adminCanActivateManualSubscriptionWithoutOrgManagerCheck() {
        UUID organizationId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        PlanEntity plan = searchPlan("pro");

        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "pro"))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, "search_saas"))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity subscription = adminBillingService.activateManualSubscription(
                organizationId,
                adminUserId,
                "search_saas",
                "pro"
        );

        assertThat(subscription.getStatus()).isEqualTo("active");
        assertThat(subscription.getProvider()).isEqualTo("manual");
        verify(entitlementSyncService).syncFromPlan(organizationId, "search_saas", "pro");
    }

    @Test
    void adminStatusChangePreservesExistingProvider() {
        UUID organizationId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        PlanEntity plan = searchPlan("pro");
        SubscriptionEntity existing = SubscriptionEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .productCode("search_saas")
                .planCode("pro")
                .status("trial")
                .provider("stripe")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(planRepository.findByProductCodeAndPlanCodeAndActiveTrue("search_saas", "pro"))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByOrganizationIdAndProductCode(organizationId, "search_saas"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity changed = adminBillingService.changeSubscription(
                organizationId,
                adminUserId,
                "search_saas",
                "pro",
                "active"
        );

        assertThat(changed.getProvider()).isEqualTo("stripe");
        verify(entitlementSyncService).syncFromPlan(organizationId, "search_saas", "pro");
    }

    @Test
    void adminChangeRejectsUnknownOrganizationBeforeWritingSubscription() {
        UUID organizationId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        when(organizationRepository.existsById(organizationId)).thenReturn(false);

        assertThatThrownBy(() -> adminBillingService.changeSubscription(
                organizationId,
                adminUserId,
                "search_saas",
                "pro",
                "active"
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Organization does not exist");

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

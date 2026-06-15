package pl.zydron.platform.platformcore.entitlements;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.zydron.platform.platformcore.tenants.TenantService;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntitlementServiceTests {

    private final OrganizationEntitlementRepository organizationEntitlementRepository = mock(OrganizationEntitlementRepository.class);
    private final UserEntitlementRepository userEntitlementRepository = mock(UserEntitlementRepository.class);
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final MetricRepository metricRepository = mock(MetricRepository.class);
    private final TenantService tenantService = mock(TenantService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EntitlementService entitlementService = new EntitlementService(
            organizationEntitlementRepository,
            userEntitlementRepository,
            featureRepository,
            metricRepository,
            tenantService,
            jdbcTemplate,
            new ObjectMapper()
    );

    @Test
    void effectiveLimitUsesOrganizationEntitlementWhenNoUserOverrideExists() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var organizationEntitlement = organizationEntitlement(
                organizationId,
                "ai_search_per_use",
                "ai_search_usage",
                BigDecimal.valueOf(100)
        );

        when(organizationEntitlementRepository.findCurrent(
                organizationId,
                "search_saas",
                "ai_search_per_use",
                "ai_search_usage"
        )).thenReturn(Optional.of(organizationEntitlement));
        when(userEntitlementRepository.findCurrent(
                organizationId,
                userId,
                "search_saas",
                "ai_search_per_use",
                "ai_search_usage"
        )).thenReturn(Optional.empty());

        var limit = entitlementService.getEffectiveLimit(
                organizationId,
                userId,
                "search_saas",
                "ai_search_per_use",
                "ai_search_usage"
        );

        assertThat(limit.enabled()).isTrue();
        assertThat(limit.limitValue()).isEqualByComparingTo("100");
        assertThat(limit.source()).isEqualTo("plan");
    }

    @Test
    void effectiveLimitUsesTighterUserOverrideWhenPresent() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(organizationEntitlementRepository.findCurrent(
                organizationId,
                "search_saas",
                "ai_search_tokens",
                "ai_search_tokens"
        )).thenReturn(Optional.of(organizationEntitlement(
                organizationId,
                "ai_search_tokens",
                "ai_search_tokens",
                BigDecimal.valueOf(100000)
        )));
        when(userEntitlementRepository.findCurrent(
                organizationId,
                userId,
                "search_saas",
                "ai_search_tokens",
                "ai_search_tokens"
        )).thenReturn(Optional.of(userEntitlement(
                organizationId,
                userId,
                "ai_search_tokens",
                "ai_search_tokens",
                BigDecimal.valueOf(30000)
        )));

        var limit = entitlementService.getEffectiveLimit(
                organizationId,
                userId,
                "search_saas",
                "ai_search_tokens",
                "ai_search_tokens"
        );

        assertThat(limit.enabled()).isTrue();
        assertThat(limit.limitValue()).isEqualByComparingTo("30000");
        assertThat(limit.source()).isEqualTo("manual");
    }

    private OrganizationEntitlementEntity organizationEntitlement(
            UUID organizationId,
            String featureCode,
            String metricCode,
            BigDecimal limitValue
    ) {
        return OrganizationEntitlementEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .productCode("search_saas")
                .featureCode(featureCode)
                .metricCode(metricCode)
                .enabled(true)
                .limitValue(limitValue)
                .period("monthly")
                .source("plan")
                .validFrom(OffsetDateTime.now())
                .build();
    }

    private UserEntitlementEntity userEntitlement(
            UUID organizationId,
            UUID userId,
            String featureCode,
            String metricCode,
            BigDecimal limitValue
    ) {
        return UserEntitlementEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .userId(userId)
                .productCode("search_saas")
                .featureCode(featureCode)
                .metricCode(metricCode)
                .enabled(true)
                .limitValue(limitValue)
                .period("monthly")
                .source("manual")
                .validFrom(OffsetDateTime.now())
                .build();
    }
}

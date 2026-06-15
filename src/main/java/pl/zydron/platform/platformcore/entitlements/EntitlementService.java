package pl.zydron.platform.platformcore.entitlements;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final OrganizationEntitlementRepository organizationEntitlementRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final FeatureRepository featureRepository;
    private final MetricRepository metricRepository;
    private final TenantService tenantService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public JsonNode getEntitlements(UUID organizationId, UUID requestingUserId, String productCode) {
        tenantService.requireActiveMember(organizationId, requestingUserId);

        String payload = jdbcTemplate.queryForObject(
                "select entitlement.get_entitlements(?, ?)",
                String.class,
                organizationId,
                productCode
        );

        try {
            return objectMapper.readTree(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid entitlements result returned by database.", exception);
        }
    }

    @Transactional
    public void syncEntitlementsFromPlan(UUID organizationId, String productCode, String planCode) {
        organizationEntitlementRepository.deletePlanEntitlements(organizationId, productCode);

        for (PlanEntitlement entitlement : planEntitlements(productCode, planCode)) {
            upsertOrganizationEntitlement(
                    organizationId,
                    productCode,
                    entitlement.featureCode(),
                    entitlement.metricCode(),
                    entitlement.limitValue(),
                    entitlement.period()
            );
        }
    }

    @Transactional(readOnly = true)
    public EntitlementLimit getEffectiveLimit(
            UUID organizationId,
            UUID userId,
            String productCode,
            String featureCode,
            String metricCode
    ) {
        var organizationEntitlement = organizationEntitlementRepository
                .findCurrent(organizationId, productCode, featureCode, metricCode)
                .orElse(null);

        if (organizationEntitlement == null) {
            return new EntitlementLimit(false, null, null, "none");
        }

        var userEntitlement = userEntitlementRepository
                .findCurrent(organizationId, userId, productCode, featureCode, metricCode)
                .orElse(null);

        if (userEntitlement == null) {
            return new EntitlementLimit(
                    organizationEntitlement.isEnabled(),
                    organizationEntitlement.getLimitValue(),
                    organizationEntitlement.getPeriod(),
                    organizationEntitlement.getSource()
            );
        }

        return new EntitlementLimit(
                organizationEntitlement.isEnabled() && userEntitlement.isEnabled(),
                tighterLimit(organizationEntitlement.getLimitValue(), userEntitlement.getLimitValue()),
                userEntitlement.getPeriod() == null ? organizationEntitlement.getPeriod() : userEntitlement.getPeriod(),
                userEntitlement.getSource()
        );
    }

    private void upsertOrganizationEntitlement(
            UUID organizationId,
            String productCode,
            String featureCode,
            String metricCode,
            BigDecimal limitValue,
            String period
    ) {
        requireFeature(productCode, featureCode);
        if (metricCode != null) {
            requireMetric(productCode, metricCode);
        }

        jdbcTemplate.update(
                """
                insert into entitlement.organization_entitlements (
                    organization_id,
                    product_code,
                    feature_code,
                    metric_code,
                    enabled,
                    limit_value,
                    period,
                    source
                )
                values (?, ?, ?, ?, true, ?, ?, 'plan')
                on conflict (organization_id, product_code, feature_code, metric_code) do update
                set enabled = true,
                    limit_value = excluded.limit_value,
                    period = excluded.period,
                    source = 'plan',
                    valid_from = now(),
                    valid_until = null
                """,
                organizationId,
                productCode,
                featureCode,
                metricCode,
                limitValue,
                period
        );
    }

    private List<PlanEntitlement> planEntitlements(String productCode, String planCode) {
        if (!"search_saas".equals(productCode)) {
            throw new BadRequestException("No entitlement template exists for product.");
        }

        return switch (planCode) {
            case "free" -> List.of(
                    new PlanEntitlement("basic_search", null, null, "monthly"),
                    new PlanEntitlement("ai_search_per_use", "ai_search_usage", BigDecimal.valueOf(100), "monthly")
            );
            case "pro" -> List.of(
                    new PlanEntitlement("basic_search", null, null, "monthly"),
                    new PlanEntitlement("ai_search_per_use", "ai_search_usage", BigDecimal.valueOf(1000), "monthly"),
                    new PlanEntitlement("ai_search_tokens", "ai_search_tokens", BigDecimal.valueOf(100000), "monthly")
            );
            default -> throw new BadRequestException("Unknown plan entitlement template.");
        };
    }

    private void requireFeature(String productCode, String featureCode) {
        featureRepository.findByProductCodeAndFeatureCodeAndActive(productCode, featureCode, true)
                .orElseThrow(() -> new BadRequestException("Feature does not exist or is not active."));
    }

    private void requireMetric(String productCode, String metricCode) {
        metricRepository.findByProductCodeAndMetricCode(productCode, metricCode)
                .orElseThrow(() -> new BadRequestException("Metric does not exist."));
    }

    private BigDecimal tighterLimit(BigDecimal organizationLimit, BigDecimal userLimit) {
        if (organizationLimit == null) {
            return userLimit;
        }
        if (userLimit == null) {
            return organizationLimit;
        }
        return organizationLimit.min(userLimit);
    }

    private record PlanEntitlement(String featureCode, String metricCode, BigDecimal limitValue, String period) {
    }
}

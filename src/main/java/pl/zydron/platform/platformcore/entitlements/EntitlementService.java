package pl.zydron.platform.platformcore.entitlements;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantService;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public EntitlementsResponse getEntitlements(UUID organizationId, UUID requestingUserId, String productCode) {
        tenantService.requireActiveMember(organizationId, requestingUserId);

        String payload = jdbcTemplate.queryForObject(
                "select entitlement.get_entitlements(?, ?)",
                String.class,
                organizationId,
                productCode
        );

        try {
            Map<String, EntitlementResponse> entitlements = objectMapper.readValue(
                    payload,
                    new TypeReference<LinkedHashMap<String, EntitlementResponse>>() {
                    }
            );
            return new EntitlementsResponse(organizationId, productCode, entitlements);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid entitlements result returned by database.", exception);
        }
    }

    @Transactional
    public void syncEntitlementsFromPlan(UUID organizationId, String productCode, String planCode) {
        var entitlements = planEntitlements(productCode, planCode);

        for (PlanEntitlement entitlement : entitlements) {
            upsertOrganizationEntitlement(
                    organizationId,
                    productCode,
                    entitlement.featureCode(),
                    entitlement.metricCode(),
                    entitlement.enabled(),
                    entitlement.limitValue(),
                    entitlement.period()
            );
        }

        disablePlanEntitlementsNotInPlan(organizationId, productCode, planCode);
    }

    @Transactional
    public void disablePlanEntitlements(UUID organizationId, String productCode) {
        jdbcTemplate.update(
                """
                update entitlement.organization_entitlements
                set enabled = false,
                    valid_until = now()
                where organization_id = ?
                  and product_code = ?
                  and source = 'plan'
                  and enabled
                """,
                organizationId,
                productCode
        );
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
                tighterLimit(organizationEntitlement, userEntitlement),
                effectivePeriod(organizationEntitlement, userEntitlement),
                userEntitlement.getSource()
        );
    }

    private void upsertOrganizationEntitlement(
            UUID organizationId,
            String productCode,
            String featureCode,
            String metricCode,
            boolean enabled,
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
                values (?, ?, ?, ?, ?, ?, ?, 'plan')
                on conflict (organization_id, product_code, feature_code, metric_code) do update
                set enabled = excluded.enabled,
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
                enabled,
                limitValue,
                period
        );
    }

    private void disablePlanEntitlementsNotInPlan(UUID organizationId, String productCode, String planCode) {
        jdbcTemplate.update(
                """
                update entitlement.organization_entitlements oe
                set enabled = false,
                    valid_until = now()
                where oe.organization_id = ?
                  and oe.product_code = ?
                  and oe.source = 'plan'
                  and not exists (
                      select 1
                      from billing.plan_entitlements pe
                      where pe.product_code = oe.product_code
                        and pe.plan_code = ?
                        and pe.feature_code = oe.feature_code
                        and pe.metric_code is not distinct from oe.metric_code
                        and pe.active
                  )
                """,
                organizationId,
                productCode,
                planCode
        );
    }

    private List<PlanEntitlement> planEntitlements(String productCode, String planCode) {
        var entitlements = jdbcTemplate.query(
                """
                select feature_code,
                       metric_code,
                       enabled,
                       limit_value,
                       period
                from billing.plan_entitlements
                where product_code = ?
                  and plan_code = ?
                  and active
                order by feature_code, metric_code nulls first
                """,
                (rs, rowNum) -> new PlanEntitlement(
                        rs.getString("feature_code"),
                        rs.getString("metric_code"),
                        rs.getBoolean("enabled"),
                        rs.getBigDecimal("limit_value"),
                        rs.getString("period")
                ),
                productCode,
                planCode
        );

        if (entitlements.isEmpty()) {
            throw new BadRequestException("No entitlement template exists for product plan.");
        }

        return entitlements;
    }

    private void requireFeature(String productCode, String featureCode) {
        featureRepository.findByProductCodeAndFeatureCodeAndActive(productCode, featureCode, true)
                .orElseThrow(() -> new BadRequestException("Feature does not exist or is not active."));
    }

    private void requireMetric(String productCode, String metricCode) {
        metricRepository.findByProductCodeAndMetricCode(productCode, metricCode)
                .orElseThrow(() -> new BadRequestException("Metric does not exist."));
    }

    private BigDecimal tighterLimit(
            OrganizationEntitlementEntity organizationEntitlement,
            UserEntitlementEntity userEntitlement
    ) {
        var organizationPeriod = organizationEntitlement.getPeriod();
        var userPeriod = userEntitlement.getPeriod();
        if (userPeriod != null && organizationPeriod != null && !userPeriod.equals(organizationPeriod)) {
            throw new BadRequestException("User entitlement period must match organization entitlement period.");
        }

        var organizationLimit = organizationEntitlement.getLimitValue();
        var userLimit = userEntitlement.getLimitValue();
        if (organizationLimit == null) {
            return userLimit;
        }
        if (userLimit == null) {
            return organizationLimit;
        }
        return organizationLimit.min(userLimit);
    }

    private String effectivePeriod(OrganizationEntitlementEntity organizationEntitlement, UserEntitlementEntity userEntitlement) {
        return userEntitlement.getPeriod() == null ? organizationEntitlement.getPeriod() : userEntitlement.getPeriod();
    }

    private record PlanEntitlement(
            String featureCode,
            String metricCode,
            boolean enabled,
            BigDecimal limitValue,
            String period
    ) {
    }

    public record EntitlementsResponse(UUID organizationId, String productCode, Map<String, EntitlementResponse> entitlements) {
    }

    public record EntitlementResponse(
            boolean enabled,
            String metricCode,
            BigDecimal limitValue,
            String period,
            String source
    ) {
    }
}

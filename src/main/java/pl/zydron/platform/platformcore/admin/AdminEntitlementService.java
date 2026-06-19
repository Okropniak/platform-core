package pl.zydron.platform.platformcore.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.OrganizationRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminEntitlementService {

    private final OrganizationRepository organizationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    @Transactional
    public EntitlementOverrideResult overrideOrganizationEntitlement(
            UUID organizationId,
            UUID adminUserId,
            String productCode,
            String featureCode,
            String metricCode,
            boolean enabled,
            BigDecimal limitValue,
            String period,
            String source
    ) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new BadRequestException("Organization does not exist.");
        }
        String effectiveSource = source == null || source.isBlank() ? "manual" : source;
        EntitlementOverrideResult result = jdbcTemplate.queryForObject(
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
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (organization_id, product_code, feature_code, metric_code) do update
                set enabled = excluded.enabled,
                    limit_value = excluded.limit_value,
                    period = excluded.period,
                    source = excluded.source,
                    valid_from = now(),
                    valid_until = null
                returning id,
                          product_code,
                          feature_code,
                          metric_code,
                          enabled,
                          limit_value,
                          period,
                          source
                """,
                (rs, rowNum) -> new EntitlementOverrideResult(
                        rs.getObject("id", UUID.class),
                        rs.getString("product_code"),
                        rs.getString("feature_code"),
                        rs.getString("metric_code"),
                        rs.getBoolean("enabled"),
                        rs.getBigDecimal("limit_value"),
                        rs.getString("period"),
                        rs.getString("source")
                ),
                organizationId,
                productCode,
                featureCode,
                metricCode,
                enabled,
                limitValue,
                period,
                effectiveSource
        );
        auditService.record(
                organizationId,
                adminUserId,
                productCode,
                "admin_entitlement_overridden",
                "organization_entitlement",
                result == null ? featureCode : result.id().toString(),
                Map.of(
                        "featureCode", featureCode,
                        "metricCode", metricCode == null ? "" : metricCode,
                        "enabled", enabled,
                        "source", effectiveSource
                )
        );
        return result;
    }

    public record EntitlementOverrideResult(
            UUID id,
            String productCode,
            String featureCode,
            String metricCode,
            boolean enabled,
            BigDecimal limitValue,
            String period,
            String source
    ) {
    }
}

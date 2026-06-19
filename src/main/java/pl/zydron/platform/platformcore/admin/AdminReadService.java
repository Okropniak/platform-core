package pl.zydron.platform.platformcore.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.billing.SubscriptionEntity;
import pl.zydron.platform.platformcore.billing.SubscriptionRepository;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.OrganizationEntity;
import pl.zydron.platform.platformcore.tenants.OrganizationRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminReadService {

    private final OrganizationRepository organizationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Page<OrganizationSummary> organizations(Pageable pageable) {
        return organizationRepository.findAll(pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public OrganizationDetail organizationDetail(UUID organizationId) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BadRequestException("Organization does not exist."));
        Long activeMembers = jdbcTemplate.queryForObject(
                """
                select count(*)
                from platform.organization_members
                where organization_id = ?
                  and status = 'active'
                """,
                Long.class,
                organizationId
        );
        return new OrganizationDetail(
                toSummary(organization),
                activeMembers == null ? 0 : activeMembers,
                subscriptionRepository.findByOrganizationIdOrderByProductCodeAsc(organizationId),
                entitlements(organizationId)
        );
    }

    @Transactional(readOnly = true)
    public List<EntitlementRow> entitlements(UUID organizationId) {
        requireOrganization(organizationId);
        return jdbcTemplate.query(
                """
                select product_code,
                       feature_code,
                       metric_code,
                       enabled,
                       limit_value,
                       period,
                       source,
                       valid_from,
                       valid_until
                from entitlement.organization_entitlements
                where organization_id = ?
                order by product_code, feature_code, metric_code nulls first
                """,
                (rs, rowNum) -> new EntitlementRow(
                        rs.getString("product_code"),
                        rs.getString("feature_code"),
                        rs.getString("metric_code"),
                        rs.getBoolean("enabled"),
                        rs.getBigDecimal("limit_value"),
                        rs.getString("period"),
                        rs.getString("source"),
                        rs.getObject("valid_from", OffsetDateTime.class),
                        rs.getObject("valid_until", OffsetDateTime.class)
                ),
                organizationId
        );
    }

    @Transactional(readOnly = true)
    public List<UsageCounterRow> usage(UUID organizationId, String productCode) {
        requireOrganization(organizationId);
        var sql = new StringBuilder("""
                select product_code,
                       metric_code,
                       counter_scope,
                       user_id,
                       period_start,
                       period_end,
                       used_value,
                       reserved_value,
                       updated_at
                from usage.usage_counters
                where organization_id = ?
                """);
        var args = new ArrayList<Object>();
        args.add(organizationId);
        if (productCode != null && !productCode.isBlank()) {
            sql.append(" and product_code = ?");
            args.add(productCode);
        }
        sql.append(" order by product_code, metric_code, period_start desc, counter_scope, user_id nulls first");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new UsageCounterRow(
                        rs.getString("product_code"),
                        rs.getString("metric_code"),
                        rs.getString("counter_scope"),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("period_start", OffsetDateTime.class),
                        rs.getObject("period_end", OffsetDateTime.class),
                        rs.getBigDecimal("used_value"),
                        rs.getBigDecimal("reserved_value"),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                args.toArray()
        );
    }

    @Transactional(readOnly = true)
    public Page<AuditRow> audit(
            UUID organizationId,
            String productCode,
            String eventType,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo,
            Pageable pageable
    ) {
        var where = new StringBuilder(" where 1 = 1");
        var args = new ArrayList<Object>();
        appendFilter(where, args, "organization_id = ?", organizationId);
        appendFilter(where, args, "product_code = ?", productCode);
        appendFilter(where, args, "event_type = ?", eventType);
        appendFilter(where, args, "created_at >= ?", dateFrom == null ? null : Timestamp.from(dateFrom.toInstant()));
        appendFilter(where, args, "created_at < ?", dateTo == null ? null : Timestamp.from(dateTo.toInstant()));

        Long total = jdbcTemplate.queryForObject(
                "select count(*) from audit.audit_events" + where,
                Long.class,
                args.toArray()
        );

        var pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());

        List<AuditRow> rows = jdbcTemplate.query(
                """
                select id,
                       organization_id,
                       user_id,
                       product_code,
                       event_type,
                       entity_type,
                       entity_id,
                       created_at
                from audit.audit_events
                """ + where + " order by created_at desc, id desc limit ? offset ?",
                (rs, rowNum) -> new AuditRow(
                        rs.getLong("id"),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("product_code"),
                        rs.getString("event_type"),
                        rs.getString("entity_type"),
                        rs.getString("entity_id"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                pageArgs.toArray()
        );
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    private OrganizationSummary toSummary(OrganizationEntity organization) {
        return new OrganizationSummary(
                organization.getId(),
                organization.getName(),
                organization.getType(),
                organization.getCreatedBy(),
                organization.getCreatedAt(),
                organization.getUpdatedAt()
        );
    }

    private void requireOrganization(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new BadRequestException("Organization does not exist.");
        }
    }

    private static void appendFilter(StringBuilder where, List<Object> args, String clause, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        where.append(" and ").append(clause);
        args.add(value);
    }

    public record OrganizationSummary(
            UUID id,
            String name,
            String type,
            UUID createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record OrganizationDetail(
            OrganizationSummary organization,
            long activeMembers,
            List<SubscriptionEntity> subscriptions,
            List<EntitlementRow> entitlements
    ) {
    }

    public record EntitlementRow(
            String productCode,
            String featureCode,
            String metricCode,
            boolean enabled,
            BigDecimal limitValue,
            String period,
            String source,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil
    ) {
    }

    public record UsageCounterRow(
            String productCode,
            String metricCode,
            String counterScope,
            UUID userId,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            BigDecimal usedValue,
            BigDecimal reservedValue,
            OffsetDateTime updatedAt
    ) {
    }

    public record AuditRow(
            long id,
            UUID organizationId,
            UUID userId,
            String productCode,
            String eventType,
            String entityType,
            String entityId,
            OffsetDateTime createdAt
    ) {
    }
}

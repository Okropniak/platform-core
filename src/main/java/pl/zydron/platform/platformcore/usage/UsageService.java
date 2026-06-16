package pl.zydron.platform.platformcore.usage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageCounterRepository usageCounterRepository;
    private final UsageReservationRepository usageReservationRepository;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public UsageResult consumeUsage(
            UUID organizationId,
            UUID userId,
            String productCode,
            String metricCode,
            BigDecimal amount,
            String idempotencyKey
    ) {
        tenantService.requireActiveMember(organizationId, userId);
        requireIdempotencyKey(idempotencyKey);

        UsageResult result = readResult(jdbcTemplate.queryForObject(
                "select usage.consume_usage(?, ?, ?, ?, ?, ?)",
                String.class,
                organizationId,
                userId,
                productCode,
                metricCode,
                amount,
                idempotencyKey
        ), UsageResult.class);
        auditUsageLimitExceeded(organizationId, userId, productCode, metricCode, result.accepted(), result.reason());
        return result;
    }

    @Transactional
    public ReservationResult reserveUsage(
            UUID organizationId,
            UUID userId,
            String productCode,
            String metricCode,
            BigDecimal amount,
            String idempotencyKey
    ) {
        tenantService.requireActiveMember(organizationId, userId);
        requireIdempotencyKey(idempotencyKey);

        ReservationResult result = readResult(jdbcTemplate.queryForObject(
                "select usage.reserve_usage(?, ?, ?, ?, ?, ?)",
                String.class,
                organizationId,
                userId,
                productCode,
                metricCode,
                amount,
                idempotencyKey
        ), ReservationResult.class);
        auditUsageLimitExceeded(organizationId, userId, productCode, metricCode, result.accepted(), result.reason());
        return result;
    }

    @Transactional
    public FinalizationResult finalizeUsage(UUID reservationId, UUID userId, BigDecimal actualAmount) {
        usageReservationRepository.findById(reservationId)
                .ifPresent(reservation -> tenantService.requireActiveMember(reservation.getOrganizationId(), userId));

        FinalizationResult result = readResult(jdbcTemplate.queryForObject(
                "select usage.finalize_usage(?, ?, ?)",
                String.class,
                reservationId,
                userId,
                actualAmount
        ), FinalizationResult.class);
        if (!result.finalized() && "limit_exceeded".equals(result.reason())) {
            usageReservationRepository.findById(reservationId)
                    .ifPresent(reservation -> auditService.record(
                            reservation.getOrganizationId(),
                            userId,
                            reservation.getProductCode(),
                            "usage_limit_exceeded",
                            "usage_reservation",
                            reservationId.toString(),
                            Map.of("metricCode", reservation.getMetricCode(), "operation", "finalize")
                    ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<UsageSummaryItem> getUsageSummary(UUID organizationId, UUID userId, String productCode) {
        tenantService.requireActiveMember(organizationId, userId);
        return usageCounterRepository.findOrganizationSummary(organizationId, productCode).stream()
                .map(counter -> new UsageSummaryItem(
                        counter.getMetricCode(),
                        counter.getPeriodStart(),
                        counter.getPeriodEnd(),
                        counter.getUsedValue(),
                        counter.getReservedValue()
                ))
                .toList();
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency key is required.");
        }
    }

    private <T> T readResult(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid usage result returned by database.", exception);
        }
    }

    private void auditUsageLimitExceeded(
            UUID organizationId,
            UUID userId,
            String productCode,
            String metricCode,
            boolean accepted,
            String reason
    ) {
        if (!accepted && "limit_exceeded".equals(reason)) {
            auditService.record(
                    organizationId,
                    userId,
                    productCode,
                    "usage_limit_exceeded",
                    "usage_metric",
                    metricCode,
                    Map.of("metricCode", metricCode)
            );
        }
    }
}

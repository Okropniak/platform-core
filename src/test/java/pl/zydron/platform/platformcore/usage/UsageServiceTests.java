package pl.zydron.platform.platformcore.usage;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.tenants.TenantService;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageServiceTests {

    private final UsageCounterRepository usageCounterRepository = mock(UsageCounterRepository.class);
    private final UsageReservationRepository usageReservationRepository = mock(UsageReservationRepository.class);
    private final TenantService tenantService = mock(TenantService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final UsageService usageService = new UsageService(
            usageCounterRepository,
            usageReservationRepository,
            tenantService,
            auditService,
            jdbcTemplate,
            new ObjectMapper()
    );

    @Test
    void finalizeUsageChecksMembershipForExistingReservationBeforeCallingSql() {
        UUID reservationId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UsageReservationEntity reservation = UsageReservationEntity.builder()
                .id(reservationId)
                .organizationId(organizationId)
                .userId(UUID.randomUUID())
                .productCode("search_saas")
                .metricCode("ai_search_tokens")
                .reservedAmount(BigDecimal.TEN)
                .counterScope("organization")
                .status("reserved")
                .idempotencyKey("search_saas:reserve:test")
                .build();

        when(usageReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(jdbcTemplate.queryForObject(
                eq("select usage.finalize_usage(?, ?, ?)"),
                eq(String.class),
                eq(reservationId),
                eq(callerId),
                eq(BigDecimal.ONE)
        )).thenReturn("{\"finalized\":false,\"reason\":\"reservation_user_mismatch\"}");

        FinalizationResult result = usageService.finalizeUsage(reservationId, callerId, BigDecimal.ONE);

        assertThat(result.reason()).isEqualTo("reservation_user_mismatch");
        verify(tenantService).requireActiveMember(organizationId, callerId);
    }
}
